// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.rename

import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.filter.EvaluationFilter


data class RenameStrategy(
  val collectContextOnly: Boolean,
  val placeholderName: String,
  val suggestionsProvider: String = DEFAULT_PROVIDER,
  override val filters: Map<String, EvaluationFilter>
) : EvaluationStrategy {

  fun isDefaultProvider(): Boolean = suggestionsProvider == DEFAULT_PROVIDER

  companion object {
    private const val DEFAULT_PROVIDER: String = "DEFAULT"
  }
}