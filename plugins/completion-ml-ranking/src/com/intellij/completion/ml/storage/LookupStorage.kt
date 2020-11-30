// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.storage

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.performance.CompletionPerformanceTracker
import com.intellij.completion.ml.sorting.RankingModelWrapper
import com.intellij.lang.Language
import com.intellij.completion.ml.personalization.session.LookupSessionFactorsStorage

interface LookupStorage {
  companion object {
    fun get(lookup: LookupImpl): LookupStorage? = MutableLookupStorage.get(lookup)

    fun get(parameters: CompletionParameters): LookupStorage? = MutableLookupStorage.get(parameters)
  }

  val model: RankingModelWrapper?
  val language: Language
  val startedTimestamp: Long
  val sessionFactors: LookupSessionFactorsStorage
  val userFactors: Map<String, String>
  val contextFactors: Map<String, String>
  val performanceTracker: CompletionPerformanceTracker
  fun mlUsed(): Boolean
  fun contextProvidersResult(): ContextFeatures
  fun shouldReRank(): Boolean
  fun shouldComputeFeatures(): Boolean
  fun getItemStorage(id: String): LookupElementStorage
}