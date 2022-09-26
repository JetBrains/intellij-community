// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols

import com.intellij.lang.documentation.DocumentationResult
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.navigation.TargetPresentation
import com.intellij.webSymbols.impl.WebSymbolDocumentationTargetImpl

/*
 * DEPRECATION -> @JvmDefault
 **/
@Suppress("DEPRECATION")
interface WebSymbolDocumentationTarget : DocumentationTarget {

  val symbol: WebSymbol

  @JvmDefault
  override val presentation: TargetPresentation
    get() = TargetPresentation.builder(symbol.name)
      .icon(symbol.icon)
      .presentation()

  @JvmDefault
  override fun computeDocumentation(): DocumentationResult? =
    symbol.documentation
      ?.takeIf { it.isNotEmpty() }
      ?.let { doc -> WebSymbolDocumentationTargetImpl.buildDocumentation(doc) }
}