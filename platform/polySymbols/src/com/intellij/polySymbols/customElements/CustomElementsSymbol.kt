// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolQualifiedKind

interface CustomElementsSymbol : PolySymbol {


  companion object {

    private const val NAMESPACE_CUSTOM_ELEMENTS_MANIFEST: String = "custom-elements-manifest"

    val CEM_PACKAGES = PolySymbolQualifiedKind(NAMESPACE_CUSTOM_ELEMENTS_MANIFEST, "packages")
    val CEM_MODULES = PolySymbolQualifiedKind(NAMESPACE_CUSTOM_ELEMENTS_MANIFEST, "modules")
    val CEM_DECLARATIONS = PolySymbolQualifiedKind(NAMESPACE_CUSTOM_ELEMENTS_MANIFEST, "declarations")

  }

}