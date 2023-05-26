(ns yugabyte.ysql.counter
  "Something like YCQL 'counter' test. SQL does not have counter type though, so we just use int."
  (:require [clojure.java.jdbc :as j]
            [clojure.string :as str]
            [clojure.tools.logging :refer [debug info warn]]
            [jepsen.client :as client]
            [jepsen.reconnect :as rc]
            [yugabyte.ysql.client :as c]))

(def table-name "counter")

(defrecord YSQLCounterYbClient []
  c/YSQLYbClient

  (setup-cluster! [this test c conn-wrapper]
    ; for now psql postgres://mz_system@n1:33277
    ; but should be run automatically, maybe once at beginning
    (c/execute-notrans! c (j/drop-table-ddl table-name (:conditional? true)))
    (c/execute-notrans! c (j/create-table-ddl table-name [[:id :int "PRIMARY KEY"]
                                                  [:count :int]]))
    (c/insert! c table-name {:id 0 :count 0}))

  (invoke-op! [this test op c conn-wrapper]
    (case (:f op)
      ; update! can't handle column references
      ; Mz: ERROR: SET clause does not allow subqueries
      :add (do (c/execute-notrans! op c [(str "UPDATE " table-name " SET count = count + " (str (:value op)) " WHERE id = 0")])
               (assoc op :type :ok))

      :read (let [value (c/select-single-value op c table-name :count "id = 0")]
              (assoc op :type :ok :value value))))

  (teardown-cluster! [this test c conn-wrapper]
    (c/drop-table c table-name)))


(c/defclient YSQLCounterClient YSQLCounterYbClient)
