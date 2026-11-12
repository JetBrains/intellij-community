// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.lang.documentation.ClientDocumentationSettings
import com.intellij.lang.documentation.LocalDocumentationSettings
import com.intellij.mock.MockApplication
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer
import com.intellij.polySymbols.context.impl.PolyContextProviderExtensionPoint
import com.intellij.polySymbols.documentation.PolySymbolDocumentationCustomizer
import com.intellij.polySymbols.framework.PolySymbolFramework
import com.intellij.polySymbols.framework.PolySymbolFramework.Companion.KIND_FRAMEWORK
import com.intellij.polySymbols.html.HtmlSymbolMatchCustomizer
import com.intellij.polySymbols.query.impl.CustomElementsManifestMockScopeImpl
import com.intellij.polySymbols.query.impl.PolySymbolMockQueryExecutorFactory
import com.intellij.polySymbols.query.impl.WebTypesMockScopeImpl
import com.intellij.polySymbols.testFramework.query.doTest
import com.intellij.polySymbols.webTypes.WebTypesSymbolBase
import com.intellij.polySymbols.webTypes.impl.PolySymbolFilterEP
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import java.io.File

/**
 * Sets up a mock application with a [PolySymbolMockQueryExecutorFactory] for each test.
 *
 * The mock application registers only the extension points that the query tests need.
 * Use it in a class with `@TestFixtures`, not with `@TestApplication`.
 *
 * @param testPath the directory of the gold files of the test class.
 */
fun polySymbolsMockQueryExecutorFixture(testPath: String): TestFixture<PolySymbolsMockQueryExecutorFixture> =
  testFixture("polySymbolsMockQueryExecutorFixture") { context ->
    val disposable = Disposer.newDisposable("polySymbolsMockQueryExecutorFixture")
    val testName = PlatformTestUtil.getTestName(context.extensionContext.requiredTestMethod.name, true)
    val fixture = try {
      PolySymbolsMockQueryExecutorFixture(testPath, testName, disposable).also { it.setUp() }
    }
    catch (e: Throwable) {
      Disposer.dispose(disposable)
      throw e
    }
    initialized(fixture) {
      Disposer.dispose(disposable)
    }
  }

class PolySymbolsMockQueryExecutorFixture internal constructor(
  private val testPath: String,
  private val testName: String,
  /** The disposable of the test. It also disposes the mock application. */
  val disposable: Disposable,
) {

  val polySymbolQueryExecutorFactory: PolySymbolQueryExecutorFactory get() = service()

  internal fun setUp() {
    val application = MockApplication.setUp(disposable)
    application.registerService(PolySymbolQueryExecutorFactory::class.java, PolySymbolMockQueryExecutorFactory())
    application.registerService(ClientDocumentationSettings::class.java, LocalDocumentationSettings())
    application.extensionArea.registerExtensionPoint(
      "com.intellij.polySymbols.webTypes.filter",
      "com.intellij.polySymbols.webTypes.impl.PolySymbolFilterEP",
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
    application.extensionArea.registerExtensionPoint(
      "com.intellij.polySymbols.documentationCustomizer",
      PolySymbolDocumentationCustomizer::class.java.name,
      ExtensionPoint.Kind.BEAN_CLASS,
      true
    )
    application.extensionArea.registerExtensionPoint(
      "com.intellij.polySymbols.matchCustomizerFactory",
      PolySymbolMatchCustomizerFactory::class.java.name,
      ExtensionPoint.Kind.INTERFACE,
      true
    )
    application.extensionArea.registerExtensionPoint(
      "com.intellij.polySymbols.framework",
      PolySymbolFramework::class.java.name,
      ExtensionPoint.Kind.BEAN_CLASS,
      true
    )

    val mockPluginDescriptor = DefaultPluginDescriptor(PluginId.getId("mock"),
                                                       WebTypesSymbolBase::class.java.classLoader)
    application.extensionArea.getExtensionPoint<PolySymbolFilterEP>("com.intellij.polySymbols.webTypes.filter")
      .registerExtension(
        PolySymbolFilterEP().also {
          it.name = "match-prefix"
          it.implementation = "com.intellij.polySymbols.webTypes.impl.PolySymbolMatchPrefixFilter"
          it.pluginDescriptor = mockPluginDescriptor
        },
        mockPluginDescriptor, disposable)

    application.extensionArea.getExtensionPoint<PolySymbolMatchCustomizerFactory>("com.intellij.polySymbols.matchCustomizerFactory")
      .registerExtension(HtmlSymbolMatchCustomizer.Factory(), disposable)
  }

  fun registerFiles(framework: String?, webTypes: List<String>, customElementManifests: List<String>) {
    framework?.let { (polySymbolQueryExecutorFactory as PolySymbolMockQueryExecutorFactory).context[KIND_FRAMEWORK] = it }
    if (webTypes.isNotEmpty()) {
      polySymbolQueryExecutorFactory.addScope(WebTypesMockScopeImpl(disposable).also { scope ->
        webTypes.forEach { file ->
          scope.registerFile(File(testPath, "../$file.web-types.json").takeIf { it.exists() }
                             ?: File(testPath, "../../common/$file.web-types.json"))
        }
      }, null, disposable)
    }
    if (customElementManifests.isNotEmpty()) {
      polySymbolQueryExecutorFactory.addScope(CustomElementsManifestMockScopeImpl(disposable).also { scope ->
        customElementManifests.forEach { file ->
          scope.registerFile(File(testPath, "../$file.custom-elements-manifest.json").takeIf { it.exists() }
                             ?: File(testPath, "../../common/$file.custom-elements-manifest.json"))
        }
      }, null, disposable)
    }
  }

  /** Compares the result of [test] with the `<test name>.gold.txt` file in [testPath]. */
  fun doTest(test: () -> String) {
    doTest(testPath, testName, test)
  }
}
