// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes.impl

import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.webTypes.WebTypesSymbolFactory

class WebTypesSymbolFactoryEP internal constructor() : CustomLoadingExtensionPointBean<WebTypesSymbolFactory>() {

  companion object {
    private val EP_NAME = ExtensionPointName<WebTypesSymbolFactoryEP>("com.intellij.polySymbols.webTypes.symbolFactory")

    fun get(qualifiedKind: PolySymbolQualifiedKind): WebTypesSymbolFactory? =
      EP_NAME.getByKey(qualifiedKind, WebTypesSymbolFactory::class.java) { PolySymbolQualifiedKind[it.namespace ?: "", it.kind ?: ""] }
        ?.instance
  }

  @Attribute("namespace")
  @JvmField
  var namespace: String? = null

  @Attribute("kind")
  @JvmField
  var kind: String? = null

  @Attribute("implementation")
  @JvmField
  var implementation: String? = null

  override fun getImplementationClassName(): String? =
    implementation
}