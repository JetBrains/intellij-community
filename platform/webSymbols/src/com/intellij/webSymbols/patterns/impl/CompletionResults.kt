// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns.impl

import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem

internal data class CompletionResults(val items: List<WebSymbolCodeCompletionItem>,
                                      val required: Boolean = true) {

  constructor(item: WebSymbolCodeCompletionItem,
              required: Boolean = true,
              stop: Boolean = false) : this(listOf(item.withStopSequencePatternEvaluation(stop)), required)

  val stop: Boolean get() = items.any { it.stopSequencePatternEvaluation }

}