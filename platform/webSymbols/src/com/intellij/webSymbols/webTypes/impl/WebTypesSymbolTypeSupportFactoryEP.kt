// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes.impl

import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.util.KeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.webSymbols.webTypes.WebTypesSymbolTypeSupport
import java.util.*

internal class WebTypesSymbolTypeSupportFactoryEP : CustomLoadingExtensionPointBean<WebTypesSymbolTypeSupport.Factory>(),
                                                    KeyedLazyInstance<WebTypesSymbolTypeSupport.Factory> {

  companion object {
    val EP_NAME = KeyedExtensionCollector<WebTypesSymbolTypeSupport.Factory, String>(
      "com.intellij.webSymbols.webTypes.symbolTypeSupportFactory")
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