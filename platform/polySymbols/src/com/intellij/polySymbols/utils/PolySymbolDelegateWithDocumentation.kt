// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.utils

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.polySymbols.documentation.PolySymbolDocumentation
import com.intellij.polySymbols.documentation.PolySymbolWithDocumentation
import com.intellij.psi.PsiElement

interface PolySymbolDelegateWithDocumentation<T : PolySymbolWithDocumentation> : PolySymbolDelegate<T>, PolySymbolWithDocumentation {

  override val description: String?
    get() = delegate.description
  override val descriptionSections: Map<String, String>
    get() = delegate.descriptionSections
  override val docUrl: String?
    get() = delegate.docUrl
  override val defaultValue: String?
    get() = delegate.defaultValue

  override fun createDocumentation(location: PsiElement?): PolySymbolDocumentation? =
    delegate.createDocumentation(location)

  override fun getDocumentationTarget(location: PsiElement?): DocumentationTarget? =
    delegate.getDocumentationTarget(location)

  override fun createPointer(): Pointer<out PolySymbolDelegateWithDocumentation<T>>

}