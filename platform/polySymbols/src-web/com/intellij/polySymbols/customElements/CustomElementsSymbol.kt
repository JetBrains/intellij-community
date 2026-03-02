// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements

import com.intellij.model.Pointer
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.documentation.PolySymbolDocumentationTarget
import com.intellij.polySymbols.query.PolySymbolScope
import com.intellij.polySymbols.utils.PolySymbolTypeSupport
import com.intellij.polySymbols.utils.PolySymbolTypeSupport.TypeSupportProperty
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

interface CustomElementsSymbol : PolySymbol, PolySymbolScope {

  @get:ApiStatus.Internal
  val origin: CustomElementsJsonOrigin

  val description: @Nls String? get() = null

  val defaultValue: @NlsSafe String? get() = null

  override fun getDocumentationTarget(location: PsiElement?): DocumentationTarget? =
    PolySymbolDocumentationTarget.create(this, location) { symbol, _ ->
      description = symbol.description
      defaultValue = symbol.defaultValue
      library = symbol.origin.library + (symbol.origin.version?.takeIf { it != "0.0.0" }?.let { "@$it" } ?: "")
    }

  @PolySymbol.Property(TypeSupportProperty::class)
  val typeSupport: PolySymbolTypeSupport?
    get() = origin.typeSupport

  override fun createPointer(): Pointer<out CustomElementsSymbol>

  override fun getModificationCount(): Long = 0

  companion object {

    private const val NAMESPACE_CUSTOM_ELEMENTS_MANIFEST: String = "custom-elements-manifest"

    val CEM_PACKAGES: PolySymbolKind = PolySymbolKind[NAMESPACE_CUSTOM_ELEMENTS_MANIFEST, "packages"]
    val CEM_MODULES: PolySymbolKind = PolySymbolKind[NAMESPACE_CUSTOM_ELEMENTS_MANIFEST, "modules"]
    val CEM_DECLARATIONS: PolySymbolKind = PolySymbolKind[NAMESPACE_CUSTOM_ELEMENTS_MANIFEST, "declarations"]

  }

}