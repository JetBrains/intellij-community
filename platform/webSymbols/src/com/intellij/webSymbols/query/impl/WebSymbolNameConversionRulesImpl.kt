// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query.impl

import com.intellij.webSymbols.WebSymbolQualifiedKind
import com.intellij.webSymbols.query.WebSymbolNameConversionRules
import com.intellij.webSymbols.query.WebSymbolNameConverter

internal data class WebSymbolNameConversionRulesImpl(
  override val canonicalNames: Map<WebSymbolQualifiedKind, WebSymbolNameConverter>,
  override val matchNames: Map<WebSymbolQualifiedKind, WebSymbolNameConverter>,
  override val completionVariants: Map<WebSymbolQualifiedKind, WebSymbolNameConverter>,
  override val renames: Map<WebSymbolQualifiedKind, WebSymbolNameConverter>,
) : WebSymbolNameConversionRules {
  companion object {
    val EMPTY = WebSymbolNameConversionRulesImpl(emptyMap(), emptyMap(), emptyMap(), emptyMap())
  }
}
