// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements.impl

import com.intellij.polySymbols.SymbolNamespace
import com.intellij.polySymbols.customElements.CustomElementsJsonOrigin
import com.intellij.polySymbols.customElements.CustomElementsManifestScopeBase
import com.intellij.polySymbols.customElements.CustomElementsSymbol
import com.intellij.polySymbols.customElements.json.CustomElementsPackage

class CustomElementsJavaScriptPackageSymbol(
  pkg: CustomElementsPackage,
  origin: CustomElementsJsonOrigin,
  rootScope: CustomElementsManifestScopeBase,
) : CustomElementsContainerSymbolBase<CustomElementsPackage>(origin.library, pkg, origin, rootScope) {
  override fun getConstructor(): (String, CustomElementsPackage, CustomElementsJsonOrigin, CustomElementsManifestScopeBase) -> CustomElementsContainerSymbolBase<out CustomElementsPackage> = { _, pkg, origin, rootScope ->
    CustomElementsJavaScriptPackageSymbol(pkg, origin, rootScope)
  }

  override val namespace: SymbolNamespace
    get() = CustomElementsSymbol.NAMESPACE_CUSTOM_ELEMENTS_MANIFEST

  override val kind: String
    get() = CustomElementsSymbol.KIND_CEM_PACKAGES

}