// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.framework.impl

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.KeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.webSymbols.framework.WebSymbolsFramework
import org.jetbrains.annotations.TestOnly

class WebSymbolsFrameworkRegistrationExtensionPoint<T : WebSymbolsFramework> : BaseKeyedLazyInstance<T>, KeyedLazyInstance<T> {

  @Attribute("id")
  @RequiredElement
  var id: String? = null

  @Attribute("implementation")
  @RequiredElement
  var implementation: String? = null

  internal constructor()

  @TestOnly
  constructor(framework: String, instance: T) : super(instance) {
    this.id = framework
    implementation = instance::class.java.name
  }

  override fun getImplementationClassName(): String? = implementation

  override fun getKey(): String? = id

  override fun createInstance(componentManager: ComponentManager, pluginDescriptor: PluginDescriptor): T {
    val result = super.createInstance(componentManager, pluginDescriptor)
    result.id = id!!
    return result
  }

}