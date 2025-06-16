// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements

import com.intellij.model.Pointer
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.documentation.PolySymbolWithDocumentation
import com.intellij.polySymbols.query.PolySymbolScope

interface CustomElementsSymbol : PolySymbolWithDocumentation, PolySymbolScope {

  override fun createPointer(): Pointer<out CustomElementsSymbol>

  override fun getModificationCount(): Long = 0

  companion object {

    private const val NAMESPACE_CUSTOM_ELEMENTS_MANIFEST: String = "custom-elements-manifest"

    val CEM_PACKAGES: PolySymbolQualifiedKind = PolySymbolQualifiedKind[NAMESPACE_CUSTOM_ELEMENTS_MANIFEST, "packages"]
    val CEM_MODULES: PolySymbolQualifiedKind = PolySymbolQualifiedKind[NAMESPACE_CUSTOM_ELEMENTS_MANIFEST, "modules"]
    val CEM_DECLARATIONS: PolySymbolQualifiedKind = PolySymbolQualifiedKind[NAMESPACE_CUSTOM_ELEMENTS_MANIFEST, "declarations"]

  }

}