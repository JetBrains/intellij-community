// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class ComplexPatternOptions(
  val additionalScope: PolySymbol? = null,
  val apiStatus: PolySymbolApiStatus? = null,
  val isRequired: Boolean = true,
  val priority: PolySymbol.Priority? = null,
  val repeats: Boolean = false,
  val unique: Boolean = false,
  val symbolsResolver: PolySymbolPatternSymbolsResolver? = null,
)