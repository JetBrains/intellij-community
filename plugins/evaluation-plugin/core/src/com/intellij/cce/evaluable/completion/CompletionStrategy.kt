// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.completion

import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.filter.EvaluationFilter

data class CompletionStrategy(val completionType: CompletionType,
                              val prefix: CompletionPrefix,
                              val context: CompletionContext,
                              override val filters: Map<String, EvaluationFilter>) : EvaluationStrategy

sealed class CompletionPrefix {
  object NoPrefix : CompletionPrefix()
  object CapitalizePrefix : CompletionPrefix()
  class SimplePrefix(val n: Int) : CompletionPrefix()
}

enum class CompletionType {
  BASIC,
  SMART,
  ML,
  FULL_LINE,
  CLANGD // c++ only
}

enum class CompletionContext {
  ALL,
  PREVIOUS
}
