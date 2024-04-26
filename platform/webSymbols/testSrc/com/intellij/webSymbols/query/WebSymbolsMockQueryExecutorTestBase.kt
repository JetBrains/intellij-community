// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.lang.documentation.ClientDocumentationSettings
import com.intellij.lang.documentation.LocalDocumentationSettings
import com.intellij.mock.MockApplication
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.UsefulTestCase
import com.intellij.webSymbols.context.WebSymbolsContext.Companion.KIND_FRAMEWORK
import com.intellij.webSymbols.context.impl.WebSymbolsContextProviderExtensionPoint
import com.intellij.webSymbols.query.impl.CustomElementsManifestMockScopeImpl
import com.intellij.webSymbols.query.impl.WebSymbolsMockQueryExecutorFactory
import com.intellij.webSymbols.query.impl.WebTypesMockScopeImpl
import com.intellij.webSymbols.webTypes.filters.WebSymbolsMatchPrefixFilter
import com.intellij.webSymbols.webTypes.impl.WebSymbolsFilterEP
import java.io.File

abstract class WebSymbolsMockQueryExecutorTestBase : UsefulTestCase() {

  abstract val testPath: String

  val webSymbolsQueryExecutorFactory: WebSymbolsQueryExecutorFactory get() = service()

  override fun setUp() {
    super.setUp()
    val application = MockApplication.setUp(testRootDisposable)
    application.registerService(WebSymbolsQueryExecutorFactory::class.java, WebSymbolsMockQueryExecutorFactory())
    application.registerService(ClientDocumentationSettings::class.java, LocalDocumentationSettings())
    application.extensionArea.registerExtensionPoint(
      "com.intellij.webSymbols.webTypes.filter",
      "com.intellij.webSymbols.webTypes.impl.WebSymbolsFilterEP",
      ExtensionPoint.Kind.BEAN_CLASS, true)
    application.extensionArea.registerExtensionPoint(
      "com.intellij.webSymbols.webTypes.symbolFactory",
      "com.intellij.webSymbols.webTypes.impl.WebTypesSymbolFactoryEP",
      ExtensionPoint.Kind.BEAN_CLASS, true)
    application.extensionArea.registerExtensionPoint(
      "com.intellij.webSymbols.defaultIconProvider",
      "com.intellij.webSymbols.query.WebSymbolDefaultIconProvider",
      ExtensionPoint.Kind.INTERFACE, true)
    application.extensionArea.registerExtensionPoint(
      "com.intellij.webSymbols.context",
      WebSymbolsContextProviderExtensionPoint::class.java.name,
      ExtensionPoint.Kind.BEAN_CLASS,
      true
    )
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

  fun registerFiles(framework: String?, webTypes: List<String>, customElementManifests: List<String>) {
    framework?.let { (webSymbolsQueryExecutorFactory as WebSymbolsMockQueryExecutorFactory).context[KIND_FRAMEWORK] = it }
    if (webTypes.isNotEmpty()) {
      webSymbolsQueryExecutorFactory.addScope(WebTypesMockScopeImpl(testRootDisposable).also { scope ->
        webTypes.forEach { file ->
          scope.registerFile(File(testPath, "../$file.web-types.json").takeIf { it.exists() }
                             ?: File(testPath, "../../common/$file.web-types.json"))
        }
      }, null, testRootDisposable)
    }
    if (customElementManifests.isNotEmpty()) {
      webSymbolsQueryExecutorFactory.addScope(CustomElementsManifestMockScopeImpl(testRootDisposable).also { scope ->
        customElementManifests.forEach { file ->
          scope.registerFile(File(testPath, "../$file.custom-elements-manifest.json").takeIf { it.exists() }
                             ?: File(testPath, "../../common/$file.custom-elements-manifest.json"))
        }
      }, null, testRootDisposable)
    }
  }


}