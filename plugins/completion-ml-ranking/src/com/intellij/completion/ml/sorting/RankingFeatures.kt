// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.sorting

/**
 * Immutable bundle of features describing a single completion item together with its lookup,
 * session, user and context scopes. Per-element features are attached via [withElementFeatures].
 */
class RankingFeatures(
  private val user: Map<String, Any>,
  private val context: Map<String, Any>,
  private val commonSession: Map<String, Any>,
  private val lookup: Map<String, Any>,
  private val meaningfulRelevance: Set<String>,
) {
  private var relevance: Map<String, Any> = emptyMap()
  private var additional: Map<String, Any> = emptyMap()

  /** Names of default-ranking features whose values differ between items in the lookup. */
  fun meaningfulRelevanceFeatures(): Set<String> {
    return meaningfulRelevance
  }

  /** Resolves a feature value by name, searching relevance, additional, context, session, user and lookup scopes in that order. */
  fun featureValue(name: String): Any? {
    return relevance[name] ?: additional[name] ?: context[name] ?: commonSession[name] ?: user[name] ?: lookup[name]
  }

  /** Returns `true` if a feature with the given [name] exists in any scope. */
  fun hasFeature(name: String): Boolean {
    return name in relevance || name in additional || name in context || name in commonSession || name in user || name in lookup
  }

  /** Attaches per-element default ([defaultRelevance]) and [additionalRelevance] features, then returns this same instance. */
  fun withElementFeatures(
    defaultRelevance: Map<String, Any>,
    additionalRelevance: Map<String, Any>,
  ): RankingFeatures {
    this.relevance = defaultRelevance
    this.additional = additionalRelevance

    return this
  }
}
