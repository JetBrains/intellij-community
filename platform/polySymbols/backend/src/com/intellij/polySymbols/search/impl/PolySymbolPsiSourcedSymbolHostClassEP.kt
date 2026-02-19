// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.search.impl

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.psi.PsiElement
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute

internal class PolySymbolPsiSourcedSymbolHostClassEP : BaseKeyedLazyInstance<Class<PsiElement>>() {

  companion object {
    val EP_NAME = ExtensionPointName<PolySymbolPsiSourcedSymbolHostClassEP>("com.intellij.polySymbols.psiSourcedSymbol")
  }

  @Attribute("host")
  @JvmField
  var host: String? = null

  override fun getImplementationClassName(): String? = null

  override fun createInstance(componentManager: ComponentManager, pluginDescriptor: PluginDescriptor): Class<PsiElement> =
    componentManager.loadClass(host!!, pluginDescriptor)

}