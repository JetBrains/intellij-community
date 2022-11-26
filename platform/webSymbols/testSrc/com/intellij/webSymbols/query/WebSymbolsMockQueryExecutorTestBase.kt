// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.mock.MockApplication
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.UsefulTestCase
import com.intellij.webSymbols.context.WebSymbolsContext.Companion.KIND_FRAMEWORK
import com.intellij.webSymbols.webTypes.filters.WebSymbolsMatchPrefixFilter
import com.intellij.webSymbols.webTypes.impl.WebSymbolsFilterEP
import com.intellij.webSymbols.query.impl.WebSymbolsMockQueryExecutorFactory
import com.intellij.webSymbols.query.impl.WebTypesMockScopeImpl
import java.io.File

abstract class WebSymbolsMockQueryExecutorTestBase : UsefulTestCase() {

  abstract val testPath: String

  val webSymbolsQueryExecutorFactory: WebSymbolsQueryExecutorFactory get() = service()

  override fun setUp() {
    super.setUp()
    val application = MockApplication.setUp(testRootDisposable)
    application.registerService(WebSymbolsQueryExecutorFactory::class.java, WebSymbolsMockQueryExecutorFactory())
    application.extensionArea.registerExtensionPoint(
      "com.intellij.webSymbols.webTypes.filter",
      "com.intellij.webSymbols.webTypes.impl.WebSymbolsFilterEP",
      ExtensionPoint.Kind.BEAN_CLASS, true)
    application.extensionArea.registerExtensionPoint(
      "com.intellij.webSymbols.defaultIconProvider",
      "com.intellij.webSymbols.query.WebSymbolDefaultIconProvider",
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
    framework?.let { (webSymbolsQueryExecutorFactory as WebSymbolsMockQueryExecutorFactory).context[KIND_FRAMEWORK] = it }
    webSymbolsQueryExecutorFactory.addScope(WebTypesMockScopeImpl().also { scope ->
      webTypes.forEach {
        scope.registerFile(File(testPath, "../$it.web-types.json").takeIf { it.exists() }
                               ?: File(testPath, "../../common/$it.web-types.json"))
      }
    }, null, testRootDisposable)
  }


}