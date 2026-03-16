// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes.impl

import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.webTypes.WebTypesSymbolFactory
import com.intellij.util.xmlb.annotations.Attribute

class WebTypesSymbolFactoryEP internal constructor() : CustomLoadingExtensionPointBean<WebTypesSymbolFactory>() {

  companion object {
    private val EP_NAME = ExtensionPointName<WebTypesSymbolFactoryEP>("com.intellij.polySymbols.webTypes.symbolFactory")

    fun get(kind: PolySymbolKind): WebTypesSymbolFactory? =
      EP_NAME.getByKey(kind, WebTypesSymbolFactory::class.java) { PolySymbolKind[it.namespace ?: "", it.kindName ?: ""] }
        ?.instance
  }

  @Attribute("namespace")
  @JvmField
  var namespace: String? = null

  @Attribute("kindName")
  @JvmField
  var kindName: String? = null

  @Attribute("implementation")
  @JvmField
  var implementation: String? = null

  override fun getImplementationClassName(): String? =
    implementation
}