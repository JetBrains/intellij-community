package com.intellij.javascript.web.webTypes.registry

import com.intellij.javascript.web.symbols.WebSymbolsRegistryManager
import com.intellij.javascript.web.symbols.filters.WebSymbolsMatchPrefixFilter
import com.intellij.javascript.web.symbols.impl.WebSymbolsFilterEP
import com.intellij.javascript.web.webTypes.WebTypesSymbolTypeResolver
import com.intellij.javascript.web.webTypes.json.WebTypes
import com.intellij.javascript.web.webTypes.registry.impl.WebSymbolsMockRegistryManager
import com.intellij.javascript.web.webTypes.registry.impl.WebTypesMockContainerImpl
import com.intellij.mock.MockApplication
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.UsefulTestCase
import java.io.File

abstract class WebTypesMockRegistryTestBase : UsefulTestCase() {

  abstract val testPath: String

  val webSymbolsRegistryManager: WebSymbolsRegistryManager get() = service()

  override fun setUp() {
    super.setUp()
    val application = MockApplication.setUp(testRootDisposable)
    application.registerService(WebSymbolsRegistryManager::class.java, WebSymbolsMockRegistryManager())
    application.extensionArea.registerExtensionPoint(
      "com.intellij.javascript.web.filter",
      "com.intellij.javascript.web.symbols.impl.WebSymbolsFilterEP",
      ExtensionPoint.Kind.BEAN_CLASS, true)
    application.extensionArea.registerExtensionPoint(
      "com.intellij.javascript.web.defaultIconProvider",
      "com.intellij.javascript.web.symbols.WebSymbolDefaultIconProvider",
      ExtensionPoint.Kind.INTERFACE, true)
    val mockPluginDescriptor = DefaultPluginDescriptor(PluginId.getId("mock"),
                                                       WebSymbolsMatchPrefixFilter::class.java.classLoader)
    application.extensionArea.getExtensionPoint<WebSymbolsFilterEP>("com.intellij.javascript.web.filter")
      .registerExtension(
        WebSymbolsFilterEP().also {
          it.name = "match-prefix"
          it.implementation = "com.intellij.javascript.web.symbols.filters.WebSymbolsMatchPrefixFilter"
          it.pluginDescriptor = mockPluginDescriptor
        },
        mockPluginDescriptor, testRootDisposable)

  }

  fun registerFiles(framework: String?, vararg webTypes: String) {
    (webSymbolsRegistryManager as WebSymbolsMockRegistryManager).framework = framework
    webSymbolsRegistryManager.addSymbolsContainer(WebTypesMockContainerImpl().also { container ->
      webTypes.forEach {
        container.registerFile(File(testPath, "../$it.web-types.json").takeIf { it.exists() }
                               ?: File(testPath, "../../common/$it.web-types.json"))
      }
    }, null, testRootDisposable)
  }


}