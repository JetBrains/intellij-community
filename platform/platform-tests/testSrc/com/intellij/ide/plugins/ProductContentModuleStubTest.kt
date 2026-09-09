// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.ide.bootstrap.ZipFilePoolImpl
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.productMode.ProductMode
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.dependencies
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.rules.InMemoryFsExtension
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.xml.dom.createXmlStreamReader
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * A product content module without a classes root loads as a stub. The stub keeps the `<dependencies>` of its embedded
 * body, so the product mode excludes the stub and its dependents as it excludes the real module.
 */
class ProductContentModuleStubTest {
  init {
    Logger.setFactory(TestLoggerFactory::class.java)
    Logger.setUnitTestMode()
    PluginManagerCore.isUnitTestMode = true
  }

  @RegisterExtension
  @JvmField
  val inMemoryFs = InMemoryFsExtension()

  private val pluginsDirPath get() = inMemoryFs.fs.getPath("/wd/plugins")

  @ParameterizedTest
  @ValueSource(strings = ["monolith", "frontend"])
  fun `a stub that reaches an unavailable module is excluded with its dependents`(appMode: String) {
    plugin("foo") {
      content(namespace = PluginModuleId.JETBRAINS_NAMESPACE) {
        module(DEPENDENT_MODULE) {
          dependencies { module(STUB_MODULE, namespace = PluginModuleId.JETBRAINS_NAMESPACE) }
        }
      }
    }.installAt(pluginsDirPath)

    val pluginSet = PluginSetTestBuilder.fromDescriptors { loadingContext ->
      val core = loadCoreProductPlugin(
        loadingContext = loadingContext,
        pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER,
        useCoreClassLoader = true,
        reader = createXmlStreamReader(CORE_DESCRIPTOR.toByteArray()),
        isRunningFromSourcesWithoutDevBuild = false,
        isDeprecatedLoader = false,
        pool = ZipFilePoolImpl(),
        jarFileForModule = { _, _ -> null },
      )
      val plugins = pluginsDirPath.directoryStreamIfExists { it.sorted() }!!
        .mapNotNull { loadDescriptorFromFileOrDir(it, loadingContext, ZipFilePoolImpl()) }
      listOf(core) + plugins
    }
      .withProductMode(ProductMode.findById(appMode)!!)
      .build(configureClassLoaders = false)

    val stub = PluginModuleId(STUB_MODULE, PluginModuleId.JETBRAINS_NAMESPACE)
    val dependent = PluginModuleId(DEPENDENT_MODULE, PluginModuleId.JETBRAINS_NAMESPACE)
    assertThat(pluginSet.isPluginEnabled(PluginId.getId("foo"))).isTrue()
    val expected = appMode == "monolith"
    assertThat(pluginSet.isModuleEnabled(stub)).describedAs(pluginSet.exclusionReason(stub)).isEqualTo(expected)
    assertThat(pluginSet.isModuleEnabled(dependent)).describedAs(pluginSet.exclusionReason(dependent)).isEqualTo(expected)
  }

  private fun PluginSet.exclusionReason(moduleId: PluginModuleId): String {
    val module = resolvedPluginSet.candidateSet.resolveContentModuleId(moduleId) ?: return "${moduleId.displayName} is not a candidate"
    return "${moduleId.displayName}: ${resolvedPluginSet.getExclusionReason(module)}"
  }

  private companion object {
    const val STUB_MODULE = "intellij.test.stub"
    const val DEPENDENT_MODULE = "foo.dependent"

    /**
     * The core descriptor with two optional content modules whose bodies are embedded and whose classes roots are absent.
     * The product mode makes `intellij.platform.backend` unavailable in a frontend, and the stub depends on it.
     */
    @Language("XML")
    val CORE_DESCRIPTOR = """
      <idea-plugin>
        <id>com.intellij</id>
        <name>IDEA CORE</name>
        <content namespace="jetbrains">
          <module name="intellij.platform.backend"><![CDATA[<idea-plugin/>]]></module>
          <module name="$STUB_MODULE"><![CDATA[<idea-plugin><dependencies><module name="intellij.platform.backend"/></dependencies></idea-plugin>]]></module>
        </content>
      </idea-plugin>
    """.trimIndent()
  }
}
