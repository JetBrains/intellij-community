// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes.impl

import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.util.KeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.webSymbols.webTypes.WebTypesSymbolTypeResolver
import java.util.*

internal class WebTypesSymbolTypeResolverFactoryEP : CustomLoadingExtensionPointBean<WebTypesSymbolTypeResolver.Factory>(),
                                                     KeyedLazyInstance<WebTypesSymbolTypeResolver.Factory> {

  companion object {
    val EP_NAME = KeyedExtensionCollector<WebTypesSymbolTypeResolver.Factory, String>(
      "com.intellij.webSymbols.webTypes.symbolTypeResolverFactory")
  }

  @Attribute("syntax")
  @RequiredElement
  var syntax: String? = null

  @Attribute("implementation")
  @RequiredElement
  var implementation: String? = null

  override fun getImplementationClassName(): String? = implementation

  override fun getKey(): String? = syntax?.lowercase(Locale.US)

}