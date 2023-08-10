// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.context.impl

import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.KeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.webSymbols.context.WebSymbolsContextProvider
import org.jetbrains.annotations.TestOnly

class WebSymbolsContextProviderExtensionPoint : BaseKeyedLazyInstance<WebSymbolsContextProvider>, KeyedLazyInstance<WebSymbolsContextProvider> {
  // these must be public for scrambling compatibility
  @Attribute("kind")
  var kind: String? = null

  @Attribute("name")
  @RequiredElement
  var name: String? = null

  @Attribute("implementation")
  @RequiredElement
  var implementation: String? = null

  constructor() : super()

  @TestOnly
  constructor(kind: String?, name: String, instance: WebSymbolsContextProvider) : super(instance) {
    this.name = name
    this.kind = kind
    implementation = instance::class.java.name
    pluginDescriptor = DefaultPluginDescriptor("test")
  }

  override fun getImplementationClassName(): String? {
    return implementation
  }

  override fun getKey(): String? {
    return "$kind:$name"
  }
}