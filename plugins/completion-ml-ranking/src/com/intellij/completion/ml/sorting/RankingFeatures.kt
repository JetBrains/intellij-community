// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.sorting

class RankingFeatures(private val user: Map<String, Any>,
                      private val context: Map<String, Any>,
                      private val commonSession: Map<String, Any>,
                      private val lookup: Map<String, Any>,
                      private val meaningfulRelevance: Set<String>) {
  private var relevance: Map<String, Any> = emptyMap()
  private var additional: Map<String, Any> = emptyMap()

  /*
   * Names of features that affect default ranking
   */
  fun meaningfulRelevanceFeatures(): Set<String> {
    return meaningfulRelevance
  }

  fun featureValue(name: String): Any? {
    return relevance[name] ?: additional[name] ?: context[name] ?: commonSession[name] ?: user[name] ?: lookup[name]
  }

  fun hasFeature(name: String): Boolean {
    return name in relevance || name in additional || name in context || name in commonSession || name in user || name in lookup
  }

  fun withElementFeatures(defaultRelevance: Map<String, Any>,
                          additionalRelevance: Map<String, Any>): RankingFeatures {
    this.relevance = defaultRelevance
    this.additional = additionalRelevance

    return this
  }
}
