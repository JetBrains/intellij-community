// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.documentation

import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.polySymbols.PolySymbol
import com.intellij.psi.PsiElement
import javax.swing.Icon

fun interface PolySymbolDocumentationProvider<T : PolySymbol> {

  fun createDocumentation(symbol: T, location: PsiElement?): PolySymbolDocumentation

  fun computePresentation(symbol: T): TargetPresentation? = null

  fun loadIcon(path: String): Icon? = null

}