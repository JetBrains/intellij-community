// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns

import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolApiStatus

data class ComplexPatternOptions(
  val additionalScope: WebSymbol? = null,
  val apiStatus: WebSymbolApiStatus? = null,
  val isRequired: Boolean = true,
  val priority: WebSymbol.Priority? = null,
  val proximity: Int? = null,
  val repeats: Boolean = false,
  val unique: Boolean = false,
  val symbolsResolver: WebSymbolsPatternSymbolsResolver? = null,
)