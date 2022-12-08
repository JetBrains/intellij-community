// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns

import com.intellij.webSymbols.WebSymbol

data class ComplexPatternOptions(
  val additionalScope: WebSymbol?,
  val isDeprecated: Boolean?,
  val isRequired: Boolean,
  val priority: WebSymbol.Priority?,
  val proximity: Int?,
  val repeats: Boolean,
  val unique: Boolean,
  val symbolsResolver: WebSymbolsPatternSymbolsResolver?,
)