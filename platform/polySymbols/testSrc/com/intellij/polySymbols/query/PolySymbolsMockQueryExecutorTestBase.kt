// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.lang.documentation.ClientDocumentationSettings
import com.intellij.lang.documentation.LocalDocumentationSettings
import com.intellij.mock.MockApplication
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.UsefulTestCase
import com.intellij.polySymbols.context.PolyContext.Companion.KIND_FRAMEWORK
import com.intellij.polySymbols.context.impl.PolyContextProviderExtensionPoint
import com.intellij.polySymbols.query.impl.CustomElementsManifestMockScopeImpl
import com.intellij.polySymbols.query.impl.PolySymbolsMockQueryExecutorFactory
import com.intellij.polySymbols.query.impl.WebTypesMockScopeImpl
import com.intellij.polySymbols.webTypes.filters.PolySymbolsMatchPrefixFilter
import com.intellij.polySymbols.webTypes.impl.PolySymbolsFilterEP
import java.io.File

abstract class PolySymbolsMockQueryExecutorTestBase : UsefulTestCase() {

  abstract val testPath: String

  val polySymbolsQueryExecutorFactory: PolySymbolsQueryExecutorFactory get() = service()

  override fun setUp() {
    super.setUp()
    val application = MockApplication.setUp(testRootDisposable)
    application.registerService(PolySymbolsQueryExecutorFactory::class.java, PolySymbolsMockQueryExecutorFactory())
    application.registerService(ClientDocumentationSettings::class.java, LocalDocumentationSettings())
    application.extensionArea.registerExtensionPoint(
      "com.intellij.polySymbols.webTypes.filter",
      "com.intellij.polySymbols.webTypes.impl.PolySymbolsFilterEP",
      ExtensionPoint.Kind.BEAN_CLASS, true)
    application.extensionArea.registerExtensionPoint(
      "com.intellij.polySymbols.webTypes.symbolFactory",
      "com.intellij.polySymbols.webTypes.impl.WebTypesSymbolFactoryEP",
      ExtensionPoint.Kind.BEAN_CLASS, true)
    application.extensionArea.registerExtensionPoint(
      "com.intellij.polySymbols.defaultIconProvider",
      "com.intellij.polySymbols.query.PolySymbolDefaultIconProvider",
      ExtensionPoint.Kind.INTERFACE, true)
    application.extensionArea.registerExtensionPoint(
      "com.intellij.polySymbols.context",
      PolyContextProviderExtensionPoint::class.java.name,
      ExtensionPoint.Kind.BEAN_CLASS,
      true
    )
    val mockPluginDescriptor = DefaultPluginDescriptor(PluginId.getId("mock"),
                                                       PolySymbolsMatchPrefixFilter::class.java.classLoader)
    application.extensionArea.getExtensionPoint<PolySymbolsFilterEP>("com.intellij.polySymbols.webTypes.filter")
      .registerExtension(
        PolySymbolsFilterEP().also {
          it.name = "match-prefix"
          it.implementation = "com.intellij.polySymbols.webTypes.filters.PolySymbolsMatchPrefixFilter"
          it.pluginDescriptor = mockPluginDescriptor
        },
        mockPluginDescriptor, testRootDisposable)

  }

  fun registerFiles(framework: String?, webTypes: List<String>, customElementManifests: List<String>) {
    framework?.let { (polySymbolsQueryExecutorFactory as PolySymbolsMockQueryExecutorFactory).context[KIND_FRAMEWORK] = it }
    if (webTypes.isNotEmpty()) {
      polySymbolsQueryExecutorFactory.addScope(WebTypesMockScopeImpl(testRootDisposable).also { scope ->
        webTypes.forEach { file ->
          scope.registerFile(File(testPath, "../$file.web-types.json").takeIf { it.exists() }
                             ?: File(testPath, "../../common/$file.web-types.json"))
        }
      }, null, testRootDisposable)
    }
    if (customElementManifests.isNotEmpty()) {
      polySymbolsQueryExecutorFactory.addScope(CustomElementsManifestMockScopeImpl(testRootDisposable).also { scope ->
        customElementManifests.forEach { file ->
          scope.registerFile(File(testPath, "../$file.custom-elements-manifest.json").takeIf { it.exists() }
                             ?: File(testPath, "../../common/$file.custom-elements-manifest.json"))
        }
      }, null, testRootDisposable)
    }
  }


}