// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.documentation.impl

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.polySymbols.documentation.PolySymbolDocumentationTarget
import com.intellij.polySymbols.documentation.PolySymbolWithDocumentation
import com.intellij.psi.PsiElement
import com.intellij.psi.createSmartPointer

internal class PolySymbolDocumentationTargetImpl(
  override val symbol: PolySymbolWithDocumentation,
  override val location: PsiElement?,
) : PolySymbolDocumentationTarget {

  override fun createPointer(): Pointer<out DocumentationTarget> {
    val pointer = symbol.createPointer()
    val locationPtr = location?.createSmartPointer()
    return Pointer<DocumentationTarget> {
      pointer.dereference()?.let { PolySymbolDocumentationTargetImpl(it, locationPtr?.dereference()) }
    }
  }

  override fun computeDocumentation(): DocumentationResult? =
    symbol.createDocumentation(location)
      ?.takeIf { it.isNotEmpty() }
      ?.build(symbol.origin)

}