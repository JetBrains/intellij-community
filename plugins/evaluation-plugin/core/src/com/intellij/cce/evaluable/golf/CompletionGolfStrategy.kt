// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.golf

import com.intellij.cce.core.SuggestionSource
import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.evaluable.completion.CompletionType
import com.intellij.cce.filter.EvaluationFilter


/**
 * @param checkLine Check if expected line starts with suggestion from completion
 * @param invokeOnEachChar Close popup after unsuccessful completion and invoke again

If completion suggest only one token - this option is useless (see checkLine ↑). Suitable for full line or multiple token completions
 * @param source Take suggestions, with specific source
 *  - STANDARD  - standard non-full line completion
 *  - CODOTA    - <a href="https://plugins.jetbrains.com/plugin/7638-codota">https://plugins.jetbrains.com/plugin/7638-codota</a>
 *  - TAB_NINE  - <a href="https://github.com/codota/tabnine-intellij">https://github.com/codota/tabnine-intellij</a>
 *  - INTELLIJ  - <a href="https://jetbrains.team/p/ccrm/code/fl-inference">https://jetbrains.team/p/ccrm/code/fl-inference</a>
 * @param topN Take only N top suggestions, applying after filtering by source
 * @param suggestionsProvider Name of provider of suggestions (use DEFAULT for IDE completion)
 * @param pathToZipModel Path to zip file with ML ranking model
 * @param completionType Use ML for enabling ML ranking or BASIC for disabling it
 */
data class CompletionGolfStrategy(
  val mode: CompletionGolfMode,
  val checkLine: Boolean,
  val invokeOnEachChar: Boolean,

  val source: SuggestionSource?,
  var topN: Int,

  val suggestionsProvider: String,
  val pathToZipModel: String?,
  val completionType: CompletionType,
) : EvaluationStrategy {
  override val filters: Map<String, EvaluationFilter> = emptyMap()

  class Builder(val mode: CompletionGolfMode) {
    var checkLine: Boolean = true
    var invokeOnEachChar: Boolean = true

    var source: SuggestionSource? = null
    var topN: Int = -1

    lateinit var suggestionsProvider: String
    var pathToZipModel: String? = null
    var completionType: CompletionType = CompletionType.ML

    fun build(): CompletionGolfStrategy = CompletionGolfStrategy(
      mode = mode,
      checkLine = checkLine,
      invokeOnEachChar = invokeOnEachChar,
      source = source,
      topN = topN,
      suggestionsProvider = suggestionsProvider,
      pathToZipModel = pathToZipModel,
      completionType = completionType
    )
  }
}


enum class CompletionGolfMode {
  ALL,
  TOKENS,
}



