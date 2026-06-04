// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.sorting

import com.intellij.internal.ml.completion.DecoratingItemsPolicy

/**
 * Wraps a language-specific ML ranking model used to score completion items.
 * Obtained per language via [RankingSupport.getRankingModel].
 */
interface RankingModelWrapper {
  /** Identifier of the underlying model version, or `null` if unknown. */
  fun version(): String?

  /** Returns `true` if [features] contain everything the model needs to produce a score. */
  fun canScore(features: RankingFeatures): Boolean

  /** Predicts a relevance score for an item described by [features], or `null` if it cannot be scored. */
  fun score(features: RankingFeatures): Double?

  /** Policy deciding which top items should be visually highlighted after reranking. */
  fun decoratingPolicy(): DecoratingItemsPolicy
}
