// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.registry.impl

import com.intellij.webSymbols.WebSymbolQualifiedKind
import com.intellij.webSymbols.registry.WebSymbolNameConversionRules
import com.intellij.webSymbols.registry.WebSymbolNameConverter

data class WebSymbolNameConversionRulesImpl(
  override val canonicalNames: Map<WebSymbolQualifiedKind, WebSymbolNameConverter>,
  override val matchNames: Map<WebSymbolQualifiedKind, WebSymbolNameConverter>,
  override val nameVariants: Map<WebSymbolQualifiedKind, WebSymbolNameConverter>
) : WebSymbolNameConversionRules {
  companion object {
    val empty = WebSymbolNameConversionRulesImpl(emptyMap(), emptyMap(), emptyMap())
  }
}
