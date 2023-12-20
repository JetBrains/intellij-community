// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes.impl

import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.webSymbols.WebSymbolQualifiedKind
import com.intellij.webSymbols.webTypes.WebTypesSymbolFactory

class WebTypesSymbolFactoryEP internal constructor() : CustomLoadingExtensionPointBean<WebTypesSymbolFactory>() {

  companion object {
    private val EP_NAME = ExtensionPointName<WebTypesSymbolFactoryEP>("com.intellij.webSymbols.webTypes.symbolFactory")

    fun get(qualifiedKind: WebSymbolQualifiedKind): WebTypesSymbolFactory? =
      EP_NAME.getByKey(qualifiedKind, WebTypesSymbolFactory::class.java) { WebSymbolQualifiedKind(it.namespace ?: "", it.kind ?: "") }
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