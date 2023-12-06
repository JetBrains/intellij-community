// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.customElements.impl

import com.intellij.webSymbols.SymbolNamespace
import com.intellij.webSymbols.customElements.CustomElementsJsonOrigin
import com.intellij.webSymbols.customElements.CustomElementsManifestScopeBase
import com.intellij.webSymbols.customElements.CustomElementsSymbol
import com.intellij.webSymbols.customElements.CustomElementsSymbol.Companion.KIND_CEM_MODULES
import com.intellij.webSymbols.customElements.json.JavaScriptModule

class CustomElementsJavaScriptModuleSymbol private constructor(
  name: String,
  module: JavaScriptModule,
  origin: CustomElementsJsonOrigin,
  rootScope: CustomElementsManifestScopeBase,
) : CustomElementsContainerSymbolBase<JavaScriptModule>(name, module, origin, rootScope) {
  override fun getConstructor(): (String, JavaScriptModule, CustomElementsJsonOrigin, CustomElementsManifestScopeBase) -> CustomElementsContainerSymbolBase<out JavaScriptModule> =
    ::CustomElementsJavaScriptModuleSymbol

  override val namespace: SymbolNamespace
    get() = CustomElementsSymbol.NAMESPACE_CUSTOM_ELEMENTS_MANIFEST

  override val kind: String
    get() = KIND_CEM_MODULES

  companion object {
    fun create(module: JavaScriptModule,
               origin: CustomElementsJsonOrigin,
               rootScope: CustomElementsManifestScopeBase): CustomElementsJavaScriptModuleSymbol? {
      val name = module.path ?: return null
      return CustomElementsJavaScriptModuleSymbol(name, module, origin, rootScope)
    }
  }

}