// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.documentation

import com.intellij.navigation.TargetPresentation
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.documentation.impl.WebSymbolDocumentationTargetImpl

interface WebSymbolDocumentationTarget : DocumentationTarget {

  val symbol: WebSymbol

  override fun computePresentation(): TargetPresentation {
    return TargetPresentation.builder(symbol.name)
      .icon(symbol.icon)
      .presentation()
  }

  override fun computeDocumentation(): DocumentationResult? =
    symbol.documentation
      ?.takeIf { it.isNotEmpty() }
      ?.let { doc -> WebSymbolDocumentationTargetImpl.buildDocumentation(symbol.origin, doc) }
}