// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.query.impl.PolySymbolMatchBase
import com.intellij.polySymbols.utils.merge
import com.intellij.polySymbols.webTypes.WebTypesSymbol.Companion.PROP_HTML_ATTRIBUTE_VALUE

val PolySymbol.htmlAttributeValue: PolySymbolHtmlAttributeValue?
  get() = if (this is PolySymbolMatchBase)
    this.reversedSegments().flatMap { it.symbols }.map { it[PROP_HTML_ATTRIBUTE_VALUE] }.merge()
  else
    this[PROP_HTML_ATTRIBUTE_VALUE]