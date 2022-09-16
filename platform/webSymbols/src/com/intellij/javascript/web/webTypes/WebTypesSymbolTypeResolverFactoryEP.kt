package com.intellij.javascript.web.webTypes

import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.openapi.util.KeyedExtensionCollector
import com.intellij.util.KeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import java.util.*

internal class WebTypesSymbolTypeResolverFactoryEP : CustomLoadingExtensionPointBean<WebTypesSymbolTypeResolver.Factory>(),
                                                     KeyedLazyInstance<WebTypesSymbolTypeResolver.Factory> {

  companion object {
    val EP_NAME = KeyedExtensionCollector<WebTypesSymbolTypeResolver.Factory, String>("com.intellij.javascript.webTypes.symbolTypeResolverFactory")
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