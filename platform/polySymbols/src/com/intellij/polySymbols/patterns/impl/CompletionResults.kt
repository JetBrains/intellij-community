// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ConsistentCopyVisibility
data class CompletionResults internal constructor(
  val items: List<PolySymbolCodeCompletionItem>,
  val required: Boolean = true,
) {

  internal constructor(
    item: PolySymbolCodeCompletionItem,
    required: Boolean = true,
    stop: Boolean = false,
  ) : this(listOf(item.withStopSequencePatternEvaluation(stop)), required)

  val stop: Boolean get() = items.any { it.stopSequencePatternEvaluation }

}