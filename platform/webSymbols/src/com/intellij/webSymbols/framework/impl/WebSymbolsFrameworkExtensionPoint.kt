// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.framework.impl

import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.KeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.TestOnly

open class WebSymbolsFrameworkExtensionPoint<T : Any> : BaseKeyedLazyInstance<T>, KeyedLazyInstance<T> {
  // these must be public for scrambling compatibility
  @Attribute("framework")
  @RequiredElement
  var framework: String? = null

  @Attribute("implementation")
  @RequiredElement
  var implementation: String? = null

  constructor() : super()

  @TestOnly
  constructor(framework: String, instance: T) : super(instance) {
    this.framework = framework
    implementation = instance::class.java.name
    pluginDescriptor = DefaultPluginDescriptor("test")
  }

  override fun getImplementationClassName(): String? {
    return implementation
  }

  override fun getKey(): String? {
    return framework
  }
}