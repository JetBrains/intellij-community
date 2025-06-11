// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.customElements.impl

import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.customElements.CustomElementsJsonOrigin
import com.intellij.polySymbols.customElements.CustomElementsManifestScopeBase
import com.intellij.polySymbols.customElements.CustomElementsSymbol
import com.intellij.polySymbols.customElements.json.JavaScriptModule

class CustomElementsJavaScriptModuleSymbol private constructor(
  name: String,
  module: JavaScriptModule,
  origin: CustomElementsJsonOrigin,
  rootScope: CustomElementsManifestScopeBase,
) : CustomElementsContainerSymbolBase<JavaScriptModule>(name, module, origin, rootScope) {
  override fun getConstructor(): (String, JavaScriptModule, CustomElementsJsonOrigin, CustomElementsManifestScopeBase) -> CustomElementsContainerSymbolBase<out JavaScriptModule> =
    ::CustomElementsJavaScriptModuleSymbol

  override val qualifiedKind: PolySymbolQualifiedKind
    get() = CustomElementsSymbol.CEM_MODULES

  companion object {
    fun create(
      module: JavaScriptModule,
      origin: CustomElementsJsonOrigin,
      rootScope: CustomElementsManifestScopeBase,
    ): CustomElementsJavaScriptModuleSymbol? {
      val name = module.path ?: return null
      return CustomElementsJavaScriptModuleSymbol(name, module, origin, rootScope)
    }
  }

}