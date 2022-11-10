// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.documentation

import com.intellij.lang.documentation.DocumentationResult
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.navigation.TargetPresentation
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.documentation.impl.WebSymbolDocumentationTargetImpl

interface WebSymbolDocumentationTarget : DocumentationTarget {

  val symbol: WebSymbol

  override fun presentation(): TargetPresentation {
    return TargetPresentation.builder(symbol.name)
      .icon(symbol.icon)
      .presentation()
  }

  override fun computeDocumentation(): DocumentationResult? =
    symbol.documentation
      ?.takeIf { it.isNotEmpty() }
      ?.let { doc -> WebSymbolDocumentationTargetImpl.buildDocumentation(doc) }
}