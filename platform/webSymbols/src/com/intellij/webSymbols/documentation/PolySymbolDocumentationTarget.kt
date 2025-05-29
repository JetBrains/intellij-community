// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.documentation

import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.documentation.impl.PolySymbolDocumentationTargetImpl

interface PolySymbolDocumentationTarget : DocumentationTarget {

  val symbol: PolySymbol

  val location: PsiElement?

  override fun computePresentation(): TargetPresentation {
    return TargetPresentation.builder(symbol.name)
      .icon(symbol.icon)
      .presentation()
  }

  override fun computeDocumentation(): DocumentationResult? =
    symbol.createDocumentation(location)
      ?.takeIf { it.isNotEmpty() }
      ?.let { doc -> PolySymbolDocumentationTargetImpl.buildDocumentation(symbol.origin, doc) }
}