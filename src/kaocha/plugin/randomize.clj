(ns kaocha.plugin.randomize
  (:require [kaocha.plugin :as plugin])
  (:import [java.util Random]))

(defn rng [seed]
  (let [rng (java.util.Random. seed)]
    (fn [& _] (.nextInt rng))))

(defn straight-sort [test-plan]
  (if-let [tests (:kaocha.test-plan/tests test-plan)]
    (assoc test-plan
           :kaocha.test-plan/tests
           (->> tests
                (sort-by :kaocha.testable/id)
                (map straight-sort)))
    test-plan))

(defn rng-sort [rng test-plan]
  (if-let [tests (:kaocha.test-plan/tests test-plan)]
    (assoc test-plan
           :kaocha.test-plan/tests
           (->> tests
                (sort-by rng)
                (map (partial rng-sort rng))))
    test-plan))

(defmethod plugin/-register :kaocha.plugin/randomize [_ plugins]
  (conj plugins
        {:kaocha.plugin/id :kaocha.plugin/randomize

         :kaocha.hooks/cli-options
         (fn [opts]
           (conj opts
                 [nil  "--[no-]randomize"     "Run test namespaces and vars in random order."]
                 [nil  "--seed SEED"          "Provide a seed to determine the random order of tests."
                  :parse-fn #(Integer/parseInt %)]))

         :kaocha.hooks/config
         (fn [config]
           (let [randomize? (get-in config [:kaocha/cli-options :randomize])]
             (merge {::randomize? true}
                    (cond
                      (::seed config)
                      config

                      (get-in config [:kaocha/cli-options :seed])
                      (assoc config ::seed (get-in config [:kaocha/cli-options :seed]))

                      :else
                      (assoc config ::seed (rand-int Integer/MAX_VALUE)))
                    (when (false? randomize?)
                      {::randomize? false}))))

         :kaocha.hooks/post-load
         (fn [test-plan]
           (if (::randomize? test-plan)
             (let [rng (rng (::seed test-plan))]
               (->> test-plan
                    straight-sort
                    (rng-sort rng)))
             test-plan))

         :kaocha.hooks/pre-run
         (fn [test-plan]
           (if (::randomize? test-plan)
             (println "Running with --seed" (::seed test-plan))))}))