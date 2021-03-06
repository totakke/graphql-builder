(ns graphql-builder.core-test
  (:require [clojure.test :refer :all]
            [graphql-builder.core :refer :all]
            [clojure.edn :as edn]
            [graphql-builder.core :as core]
            [clojure.pprint :as pp]
            [clojure.string :as str]
            [graphql-builder.util :refer [nl-join variables->graphql]]
            [graphql-builder.parser :refer [parse defgraphql]]))

(def test-statements (map str/trim (edn/read-string (slurp "test/graphql_builder/resources/statements.edn"))))
(def parsed-statements (map parse test-statements))

(deftest generate-test
  ;; test if we can recreate the same GraphQL source
  (is (= test-statements
         (map (fn [s]
                (core/generated->graphql (core/generate s)))
              parsed-statements))))

(def inline-fragment-source "
query LoadStarships($starshipCount: Int!) {
  allStarships(first: $starshipCount) {
    edges {
      node {
        id
        name
        model
        costInCredits
        pilotConnection {
          edges {
            node {
              ...pilotFragment
            }
          }
        }
      }
    }
  }
}
fragment pilotFragment on Person {
  name
  homeworld { name }
}
")

(def inline-fragment-result "
query LoadStarships($starshipCount: Int!) {
  allStarships(first: $starshipCount) {
    edges {
      node {
        id
        name
        model
        costInCredits
        pilotConnection {
          edges {
            node {
              name
              homeworld {
                name
              }
            }
          }
        }
      }
    }
  }
}
")

(deftest inline-fragment-test
  (let [query-map (core/query-map (parse inline-fragment-source) {:inline-fragments true})
        query-fn (get-in query-map [:query :load-starships])]
    (is (= (str/trim inline-fragment-result)
           (get-in (query-fn) [:graphql :query])))))

(def query-source "
query LoadStarships($starshipCount: Int!) {
  allStarships(first: $starshipCount) {
    edges {
      node {
        id
        name
        model
        costInCredits
        pilotConnection {
          edges {
            node {
              ...pilotFragment
            }
          }
        }
      }
    }
  }
}
query LoadStarshipNames {
  allStarships(first: 7) {
    edges {
      node {
        name
      }
    }
  }
}
fragment pilotFragment on Person {
  name
  homeworld { name }
}
")

(def append-fragment-result "
query LoadStarships($starshipCount: Int!) {
  allStarships(first: $starshipCount) {
    edges {
      node {
        id
        name
        model
        costInCredits
        pilotConnection {
          edges {
            node {
              ...pilotFragment
            }
          }
        }
      }
    }
  }
}
fragment pilotFragment on Person {
  name
  homeworld {
    name
  }
}
")

(def namespace-query-result "
query LoadStarships($QueryNS__starshipCount: Int!) {
  QueryNS__allStarships: allStarships(first: $QueryNS__starshipCount) {
    edges {
      node {
        id
        name
        model
        costInCredits
        pilotConnection {
          edges {
            node {
              ...pilotFragment
            }
          }
        }
      }
    }
  }
}
fragment pilotFragment on Person {
  name
  homeworld {
    name
  }
}
")

(def namespace-inline-query-source "
query Foo($bar: Int!) {
  me {
    ...bazFragment
  }
}
fragment bazFragment on Qux {
  name(foo: $bar)
}
")

(def namespace-inline-query-result "
query Foo($QueryNS__bar: Int!) {
  QueryNS__me: me {
    name(foo: $QueryNS__bar)
  }
}
")

(deftest append-fragment-test
  (let [query-map (core/query-map (parse inline-fragment-source))
        query-fn (get-in query-map [:query :load-starships])]
    (is (= (str/trim append-fragment-result)
           (get-in (query-fn) [:graphql :query])))))

(deftest namespace-query-test
  (let [query-map (core/query-map (parse inline-fragment-source) {:prefix "QueryNS"})
        query-fn (get-in query-map [:query :load-starships])]
    (is (= (str/trim namespace-query-result)
           (get-in (query-fn) [:graphql :query])))))

(deftest namespace-inline-query-test
  (let [query-map (core/query-map (parse namespace-inline-query-source)
                                  {:prefix "QueryNS" :inline-fragments true})
        query-fn (get-in query-map [:query :foo])]
    (is (= (str/trim namespace-inline-query-result)
           (get-in (query-fn) [:graphql :query])))))

(def composed-query-source "
query LoadStarships($starshipCount: Int!) {
  allStarships(first: $starshipCount) {
    edges {
      node {
        id
        name
        model
        costInCredits
        pilotConnection {
          edges {
            node {
              ...pilotFragment
            }
          }
        }
      }
    }
  }
}
query LoadStarshipNames($starshipCount: Int!) {
  allStarships(first: $starshipCount) {
    edges {
      node {
        name
      }
    }
  }
}
fragment pilotFragment on Person {
  name
  homeworld { name }
}
")

(def composed-query-result "
query ComposedQuery($LoadStarships1__starshipCount: Int!, $LoadStarships2__starshipCount: Int!, $LoadStarshipNames__starshipCount: Int!) {
  LoadStarships1__allStarships: allStarships(first: $LoadStarships1__starshipCount) {
    edges {
      node {
        id
        name
        model
        costInCredits
        pilotConnection {
          edges {
            node {
              name
              homeworld {
                name
              }
            }
          }
        }
      }
    }
  }
  LoadStarships2__allStarships: allStarships(first: $LoadStarships2__starshipCount) {
    edges {
      node {
        id
        name
        model
        costInCredits
        pilotConnection {
          edges {
            node {
              name
              homeworld {
                name
              }
            }
          }
        }
      }
    }
  }
  LoadStarshipNames__allStarships: allStarships(first: $LoadStarshipNames__starshipCount) {
    edges {
      node {
        name
      }
    }
  }
}
")

(deftest composed-query-test
  (let [composed-fn (core/composed-query (parse composed-query-source)
                                         {:load-starships-1 "LoadStarships"
                                          :load-starships-2 "LoadStarships"
                                          :load-starship-names "LoadStarshipNames"})
        composed-query (composed-fn)
        unpack (:unpack composed-query)]
    (is (= (str/trim composed-query-result)
           (get-in composed-query [:graphql :query])))
    (is (= {:load-starships-1 {"foo" :bar}}
           (unpack {"LoadStarships1__foo" :bar})))))

(def composed-query-source-2 "
query Hero($episode: String!) {
  hero(episode: $episode) {
    name
  }
}
")

(def composed-query-result-2 "
query ComposedQuery($JediHero__episode: String!, $EmpireHero__episode: String!) {
  JediHero__hero: hero(episode: $JediHero__episode) {
    name
  }
  EmpireHero__hero: hero(episode: $EmpireHero__episode) {
    name
  }
}
")

(deftest composed-query-test-2
  (let [composed-fn (core/composed-query (parse composed-query-source-2)
                                         {:jedi-hero "Hero"
                                          :empire-hero "Hero"})
        composed-query (composed-fn)
        unpack (:unpack composed-query)]
    (is (= (str/trim composed-query-result-2)
           (get-in composed-query [:graphql :query])))))

(def composed-query-result-3 "
query ComposedQuery($LoadStarships1__starshipCount: Int!) {
  LoadStarships1__allStarships: allStarships(first: $LoadStarships1__starshipCount) {
    edges {
      node {
        id
        name
        model
        costInCredits
        pilotConnection {
          edges {
            node {
              name
              homeworld {
                name
              }
            }
          }
        }
      }
    }
  }
}
")

(deftest composed-query-test-3
  (let [composed-fn (core/composed-query (parse composed-query-source)
                                         {:load-starships-1 "LoadStarships"})
        composed-query (composed-fn)
        unpack (:unpack composed-query)]
    (is (= (str/trim composed-query-result-3)
           (get-in composed-query [:graphql :query])))))

(defgraphql parsed-graphql
  "test/graphql_builder/resources/1.graphql"
  "test/graphql_builder/resources/2.graphql")

(deftest defgrapqhl-test
  (is (= {:fragment-definitions [{:name "pilotFragment",
                                  :node-type :fragment-definition,
                                  :section :fragment-definitions,
                                  :selection-set [{:field-name "name", :node-type :field}
                                                  {:field-name "homeworld",
                                                   :node-type :field,
                                                   :selection-set [{:field-name "name",
                                                                    :node-type :field}]}],
                                  :type-condition {:type-name "Person"}}],
          :operation-definitions [{:node-type :operation-definition,
                                   :operation-type {:name "LoadStarships", :type "query"},
                                   :section :operation-definitions,
                                   :selection-set [{:field-name "allStarships",
                                                    :node-type :field,
                                                    :selection-set [{:field-name "name",
                                                                     :node-type :field}]}]}]}
         parsed-graphql)))

(def query-custom-type-test-source "
mutation createSiteWithSchema($name: String!, $label: String!, $contentSchema: [ContentFieldInput]!) {
  createSiteWithSchema(name: $name, label: $label, contentSchema: $contentSchema) {
    id
    name
    label
    contentSchema {
      ...contentSchemaSelection
    }
  }
}
fragment contentSchemaSelection on ContentField {
  type
  fieldType
  constraints
  extendsType
  allowedType
  allowedTypes
  fields {
    fieldType
    fieldName
  }
}
")

(deftest query-custom-type-test
  (is (= (str/trim query-custom-type-test-source)
         (core/generated->graphql (core/generate (parse query-custom-type-test-source))))))


(deftest variables->graphql-test
  (is (= {"fooBar" "baz"}
         (variables->graphql {:foo-bar "baz"}))))


(def nested-fragment-source "
query User {
  user {
    ...userFields
  }
}

fragment userFields on User {
  name
  messages {
    ...messageFields
  }
}

fragment messageFields on Message {
  title
}
")

(def nested-fragment-result
"
query User {
  user {
    ...userFields
  }
}
fragment userFields on User {
  name
  messages {
    ...messageFields
  }
}
fragment messageFields on Message {
  title
}
")

(deftest nested-fragment-test
  (let [query-map (core/query-map (parse nested-fragment-source) {})
        query-fn (get-in query-map [:query :user])]
    (is (= (str/trim nested-fragment-result)
           (get-in (query-fn) [:graphql :query])))))

(def inline-nested-fragment-source "
query User {
  user {
    ...userFields
  }
}

fragment userFields on User {
  name
  messages {
    ...messageFields
  }
}

fragment messageFields on Message {
  title
}
")

(def inline-nested-fragment-result
"
query User {
  user {
    name
    messages {
      title
    }
  }
}
")

(deftest inline-nested-fragment-test
  (let [query-map (core/query-map (parse inline-nested-fragment-source) {:inline-fragments true})
        query-fn (get-in query-map [:query :user])]
    (is (= (str/trim inline-nested-fragment-result)
           (get-in (query-fn) [:graphql :query])))))


(def fragment-nesting-on-same-type "

mutation validateOrderPersonalInformation($input: ValidateOrderPersonalInformationInput!) {
  validateOrderPersonalInformation(input: $input) {
    ...validateOrderPersonalInformationPayloadFields
  }
}

fragment validateOrderPersonalInformationPayloadFields on ValidateOrderPersonalInformationPayload {
  clientMutationId
  errors {
    ...errorFields
  }
  valid
}

fragment errorFields on Error {
  ...innerErrorFields
  suberrors {
    ...innerErrorFields
    suberrors {
      ...innerErrorFields
      suberrors {
        ...innerErrorFields
      }
    }
  }
}

fragment innerErrorFields on Error {
  index
  key
  messages
}
")

(def fragment-nesting-on-same-type-result
"
mutation validateOrderPersonalInformation($input: ValidateOrderPersonalInformationInput!) {
  validateOrderPersonalInformation(input: $input) {
    clientMutationId
    errors {
      index
      key
      messages
      suberrors {
        index
        key
        messages
        suberrors {
          index
          key
          messages
          suberrors {
            index
            key
            messages
          }
        }
      }
    }
    valid
  }
}
")


(deftest fragment-nesting-on-same-type-test
  (let [query-map (core/query-map (parse fragment-nesting-on-same-type) {:inline-fragments true})
        query-fn (get-in query-map [:mutation :validate-order-personal-information])]
    (is (= (str/trim fragment-nesting-on-same-type-result)
           (get-in (query-fn) [:graphql :query])))))


(def required-inside-array
"
mutation update($id: ID, $description: String, $otherIds: [ID!]) {
  updateService(id: $id, description: $description, otherIds: $otherIds) {
    ...item
  }
}
")

(deftest required-inside-array-test
  (let [query-map (core/query-map (parse required-inside-array) {})
        query-fn (get-in query-map [:mutation :update])]
    (is (= (str/trim required-inside-array)
           (get-in (query-fn) [:graphql :query])))))


(def required-inside-array-and-required-array
"
mutation update($id: ID, $description: String, $otherIds: [ID!]!) {
  updateService(id: $id, description: $description, otherIds: $otherIds) {
    ...item
  }
}
")

(deftest required-inside-array-and-required-array-test
  (let [query-map (core/query-map (parse required-inside-array) {})
        query-fn (get-in query-map [:mutation :update])]
    (is (= (str/trim required-inside-array)
           (get-in (query-fn) [:graphql :query])))))
