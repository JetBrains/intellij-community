// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.storage

import com.intellij.codeInsight.completion.BaseCompletionParameters
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.performance.MLCompletionPerformanceTracker
import com.intellij.completion.ml.personalization.session.LookupSessionFactorsStorage
import com.intellij.completion.ml.sorting.RankingModelWrapper
import com.intellij.lang.Language

/**
 * Read-only per-lookup ML state: the ranking model, the factors collected during completion,
 * and the flags that gate feature computation and reranking for a single completion session.
 */
interface LookupStorage {
  companion object {
    /** Returns the ML storage attached to [lookup], or `null` if none was created. */
    fun getStorage(lookup: LookupImpl): LookupStorage? = MutableLookupStorage.getMutableLookupStorage(lookup)

    /** Returns the ML storage associated with [parameters] (falling back to the active lookup), or `null`. */
    fun getStorage(parameters: BaseCompletionParameters): LookupStorage? = MutableLookupStorage.getMutableLookupStorage(parameters)
  }

  /** Ranking model for this lookup's language, or `null` when ML ranking is unavailable/disabled. */
  val model: RankingModelWrapper?

  /** Language the completion was invoked in. */
  val language: Language

  /** Wall-clock timestamp (ms) when the lookup was created. */
  val startedTimestamp: Long

  /** Mutable storage of session-level factors accumulated as the user interacts with the lookup. */
  val sessionFactors: LookupSessionFactorsStorage

  /** User personalization factors (application- and project-level), keyed by factor id. */
  val userFactors: Map<String, String>

  /** Context factors describing the completion environment (file/project state). */
  val contextFactors: Map<String, String>

  /** Tracker collecting feature-computation and scoring timings for this lookup. */
  val performanceTracker: MLCompletionPerformanceTracker

  /** Whether ML reranking was actually applied in this session. */
  fun mlUsed(): Boolean

  /** Context features computed by context providers for this lookup. */
  fun contextProvidersResult(): ContextFeatures

  /** Whether items should be reordered by the ML model. */
  fun shouldReRank(): Boolean

  /** Whether features should be computed at all (for reranking or for logging). */
  fun shouldComputeFeatures(): Boolean

  /** Per-item ML storage for the lookup element with the given [id]. */
  fun getItemStorage(id: String): LookupElementStorage
}