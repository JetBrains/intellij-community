// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements

import com.intellij.model.Pointer
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.documentation.PolySymbolDocumentationTarget
import com.intellij.polySymbols.query.PolySymbolScope
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls

interface CustomElementsSymbol : PolySymbol, PolySymbolScope {

  val description: @Nls String? get() = null

  val defaultValue: @NlsSafe String? get() = null

  override fun getDocumentationTarget(location: PsiElement?): DocumentationTarget? =
    PolySymbolDocumentationTarget.create(this, location) { symbol, _ ->
      description = symbol.description
      defaultValue = symbol.defaultValue
    }

  override fun createPointer(): Pointer<out CustomElementsSymbol>

  override fun getModificationCount(): Long = 0

  companion object {

    private const val NAMESPACE_CUSTOM_ELEMENTS_MANIFEST: String = "custom-elements-manifest"

    val CEM_PACKAGES: PolySymbolKind = PolySymbolKind[NAMESPACE_CUSTOM_ELEMENTS_MANIFEST, "packages"]
    val CEM_MODULES: PolySymbolKind = PolySymbolKind[NAMESPACE_CUSTOM_ELEMENTS_MANIFEST, "modules"]
    val CEM_DECLARATIONS: PolySymbolKind = PolySymbolKind[NAMESPACE_CUSTOM_ELEMENTS_MANIFEST, "declarations"]

  }

}