// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.golf

import com.intellij.cce.core.SuggestionSource
import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.filter.EvaluationFilter


/**
 * @param checkLine Check if expected line starts with suggestion from completion
 * @param invokeOnEachChar Close popup after unsuccessful completion and invoke again
 * @param checkToken In case first token in suggestion equals to first token in expected string, we can pick only first token from suggestion.

If completion suggest only one token - this option is useless (see checkLine ↑). Suitable for full line or multiple token completions
 * @param source Take suggestions, with specific source
 *  - STANDARD  - standard non-full line completion
 *  - CODOTA    - <a href="https://plugins.jetbrains.com/plugin/7638-codota">https://plugins.jetbrains.com/plugin/7638-codota</a>
 *  - TAB_NINE  - <a href="https://github.com/codota/tabnine-intellij">https://github.com/codota/tabnine-intellij</a>
 *  - INTELLIJ  - <a href="https://jetbrains.team/p/ccrm/code/fl-inference">https://jetbrains.team/p/ccrm/code/fl-inference</a>
 * @param topN Take only N top suggestions, applying after filtering by source
 * @param isBenchmark Call completion once for each token.
 * @param randomSeed Random seed for evaluation. Currently used to select token prefix in benchmark mode.
 * @param suggestionsProvider Name of provider of suggestions (use DEFAULT for IDE completion)
 */
data class CompletionGolfStrategy(
  val mode: CompletionGolfMode,
  val checkLine: Boolean = true,
  val invokeOnEachChar: Boolean = false,

  val checkToken: Boolean = true,
  val source: SuggestionSource? = null,
  var topN: Int = -1,

  val suggestionsProvider: String = DEFAULT_PROVIDER) : EvaluationStrategy {
  override val filters: Map<String, EvaluationFilter> = emptyMap()

  fun isDefaultProvider(): Boolean = suggestionsProvider == DEFAULT_PROVIDER

  companion object {
    private const val DEFAULT_PROVIDER: String = "DEFAULT"
  }
}


enum class CompletionGolfMode {
  ALL,
  TOKENS
}



