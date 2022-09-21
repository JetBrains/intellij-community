// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns.impl

import com.intellij.webSymbols.WebSymbolsCodeCompletionQueryParams
import com.intellij.webSymbols.WebSymbolsRegistry

internal class CompletionParameters(name: String,
                                    registry: WebSymbolsRegistry,
                                    val position: Int) : MatchParameters(name, registry) {
  constructor(name: String, params: WebSymbolsCodeCompletionQueryParams)
    : this(name, params.registry, params.position)

  override fun toString(): String =
    "complete: $name (position: $position, framework: $framework)"

  fun withPosition(position: Int): CompletionParameters =
    CompletionParameters(name, registry, position)
}