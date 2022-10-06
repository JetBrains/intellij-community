// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.registry

import com.intellij.mock.MockApplication
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.UsefulTestCase
import com.intellij.webSymbols.context.WebSymbolsContext.Companion.KIND_FRAMEWORK
import com.intellij.webSymbols.webTypes.filters.WebSymbolsMatchPrefixFilter
import com.intellij.webSymbols.webTypes.impl.WebSymbolsFilterEP
import com.intellij.webSymbols.registry.impl.WebSymbolsMockRegistryManager
import com.intellij.webSymbols.registry.impl.WebTypesMockContainerImpl
import java.io.File

abstract class WebSymbolsMockRegistryTestBase : UsefulTestCase() {

  abstract val testPath: String

  val webSymbolsRegistryManager: WebSymbolsRegistryManager get() = service()

  override fun setUp() {
    super.setUp()
    val application = MockApplication.setUp(testRootDisposable)
    application.registerService(WebSymbolsRegistryManager::class.java, WebSymbolsMockRegistryManager())
    application.extensionArea.registerExtensionPoint(
      "com.intellij.webSymbols.webTypes.filter",
      "com.intellij.webSymbols.webTypes.impl.WebSymbolsFilterEP",
      ExtensionPoint.Kind.BEAN_CLASS, true)
    application.extensionArea.registerExtensionPoint(
      "com.intellij.webSymbols.defaultIconProvider",
      "com.intellij.webSymbols.registry.WebSymbolDefaultIconProvider",
      ExtensionPoint.Kind.INTERFACE, true)
    val mockPluginDescriptor = DefaultPluginDescriptor(PluginId.getId("mock"),
                                                       WebSymbolsMatchPrefixFilter::class.java.classLoader)
    application.extensionArea.getExtensionPoint<WebSymbolsFilterEP>("com.intellij.webSymbols.webTypes.filter")
      .registerExtension(
        WebSymbolsFilterEP().also {
          it.name = "match-prefix"
          it.implementation = "com.intellij.webSymbols.webTypes.filters.WebSymbolsMatchPrefixFilter"
          it.pluginDescriptor = mockPluginDescriptor
        },
        mockPluginDescriptor, testRootDisposable)

  }

  fun registerFiles(framework: String?, vararg webTypes: String) {
    framework?.let { (webSymbolsRegistryManager as WebSymbolsMockRegistryManager).context[KIND_FRAMEWORK] = it }
    webSymbolsRegistryManager.addSymbolsContainer(WebTypesMockContainerImpl().also { container ->
      webTypes.forEach {
        container.registerFile(File(testPath, "../$it.web-types.json").takeIf { it.exists() }
                               ?: File(testPath, "../../common/$it.web-types.json"))
      }
    }, null, testRootDisposable)
  }


}