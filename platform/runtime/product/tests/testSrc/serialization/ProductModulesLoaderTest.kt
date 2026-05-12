// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.serialization

import com.intellij.platform.runtime.repository.createModuleDescriptor
import com.intellij.platform.runtime.repository.createRepository
import com.intellij.platform.runtime.repository.writePluginXml
import com.intellij.platform.runtime.repository.xml
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.util.io.directoryContent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.io.path.div

private const val FILE_NAME = "product-modules.xml"

class ProductModulesLoaderTest {
  @JvmField
  @RegisterExtension
  val tempDirectory = TempDirectoryExtension()

  @Test
  fun simple() {
    val repository = createRepository(tempDirectory.rootPath,
                                      createModuleDescriptor("util", emptyList(), emptyList()),
                                      createModuleDescriptor("root", emptyList(), listOf("util")),
                                      createModuleDescriptor("plugin", listOf("plugin"), emptyList()),
    )
    writePluginXmlWithModules(tempDirectory.rootPath / "plugin", "<idea-plugin><id>plugin</id></idea-plugin>")
    val xml = generateProductModulesWithPlugin()
    val productModules = ProductModulesSerialization.loadProductModules(xml, repository)
    assertEquals("plugin", productModules.bundledPluginDescriptorModules.single().name)
  }
  
  @Test
  fun `unresolved plugin module`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      createModuleDescriptor("root", emptyList(), emptyList()),
      createModuleDescriptor("plugin", listOf("plugin"), listOf("plugin.util")),
      createModuleDescriptor("plugin.util", emptyList(), listOf("unresolved.module")),
    )
    writePluginXmlWithModules(tempDirectory.rootPath / "plugin", "plugin")
    val xml = generateProductModulesWithPlugin()
    val productModules = ProductModulesSerialization.loadProductModules(xml, repository)
    assertThat(productModules.bundledPluginDescriptorModules.single().name).isEqualTo("plugin")
  }

  @Test
  fun inclusion() {
    val repository = createRepository(
      tempDirectory.rootPath,
      createModuleDescriptor("root", listOf("root"), emptyList()),
      createModuleDescriptor("common.plugin", listOf("common.plugin"), emptyList()),
      createModuleDescriptor("additional", emptyList(), emptyList()),
      createModuleDescriptor("plugin", listOf("plugin"), emptyList()),
    )
    writePluginXmlWithModules(tempDirectory.rootPath.resolve("common.plugin"), "common")
    writePluginXmlWithModules(tempDirectory.rootPath.resolve("plugin"), "plugin")
    val rootProductModulesPath = tempDirectory.rootPath.resolve("root/META-INF/root")
    productModulesWithPlugins(plugins = listOf("common.plugin")).generate(rootProductModulesPath)

    val xmlPath = directoryContent {
      xml(FILE_NAME, """
          <product-modules>
            <include>
              <from-module>root</from-module>
            </include>
            <bundled-plugins>
              <module>plugin</module>
            </bundled-plugins>  
          </product-modules>
        """.trimIndent())
    }.generateInTempDir().resolve(FILE_NAME)
    val productModules = ProductModulesSerialization.loadProductModules(xmlPath, repository)
    assertEquals(listOf("plugin", "common.plugin"), productModules.bundledPluginDescriptorModules.map { it.name })
  }
  
  @Test
  fun `inclusion without some modules`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      createModuleDescriptor("root", listOf("root"), emptyList()),
      createModuleDescriptor("additional", emptyList(), emptyList()),
      createModuleDescriptor("plugin", listOf("plugin"), emptyList()),
      createModuleDescriptor("plugin2", listOf("plugin2"), emptyList()),
    )
    writePluginXmlWithModules(tempDirectory.rootPath.resolve("plugin"), "plugin")
    writePluginXmlWithModules(tempDirectory.rootPath.resolve("plugin2"), "plugin2")
    val rootProductModulesPath = tempDirectory.rootPath.resolve("root/META-INF/root")
    productModulesWithPlugins(
      plugins = listOf("plugin", "plugin2")
    ).generate(rootProductModulesPath)

    val xmlPath = directoryContent {
      xml(FILE_NAME, """
          <product-modules>
            <include>
              <from-module>root</from-module>
              <without-module>additional</without-module>
              <without-module>plugin2</without-module>
            </include>
          </product-modules>
        """.trimIndent())
    }.generateInTempDir().resolve(FILE_NAME)
    val productModules = ProductModulesSerialization.loadProductModules(xmlPath, repository)
    val bundledPlugins = productModules.bundledPluginDescriptorModules.map { it.name }
    assertEquals(listOf("plugin"), bundledPlugins)
  }

  @Test
  fun `without module should exclude module from the nested included module`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      createModuleDescriptor("root", listOf("root"), listOf("plugin1")),
      createModuleDescriptor("plugin1", listOf("plugin1"), listOf("plugin2")),
      createModuleDescriptor("plugin2", listOf("plugin2"), listOf("plugin3")),
      createModuleDescriptor("plugin3", listOf("plugin3"), listOf()),
    )
    val plugins = listOf("plugin1", "plugin2", "plugin3")
    plugins.forEach { writePluginXmlWithModules(tempDirectory.rootPath.resolve(it), it) }
    writePluginXmlWithModules(tempDirectory.rootPath.resolve("root"), "root")
    val rootProductModulesPath = tempDirectory.rootPath.resolve("root/META-INF/root")
    directoryContent {
      xml(FILE_NAME, """
        <product-modules>
          <include>
           <from-module>plugin1</from-module>
           <without-module>plugin3</without-module>
          </include>
        </product-modules>
      """.trimIndent())
    }.generate(rootProductModulesPath)
    val plugin1ProductModulesPath = tempDirectory.rootPath.resolve("plugin1/META-INF/plugin1")
    directoryContent {
      xml(FILE_NAME, """
        <product-modules>
          <include>
           <from-module>plugin2</from-module>
          </include>
        </product-modules>
      """.trimIndent())
    }.generate(plugin1ProductModulesPath)
    val plugin2ProductModulesPath = tempDirectory.rootPath.resolve("plugin2/META-INF/plugin2")
    productModulesWithPlugins(plugins = listOf("plugin3")).generate(plugin2ProductModulesPath)
    val productModules =
      ProductModulesSerialization.loadProductModules(rootProductModulesPath.resolve(FILE_NAME), repository)
    assertThat(productModules.bundledPluginDescriptorModules.map { it.name })
      .doesNotContain("plugin3")
  }

  private fun writePluginXmlWithModules(resourcePath: Path, pluginId: String, vararg contentModules: String) {
    writePluginXml(resourcePath, """
        |<idea-plugin>
        |  <id>$pluginId</id>
        |  <content namespace="jetbrains">
        |    ${contentModules.joinToString("\n    ") { "<module name=\"$it\"/>"}}
        |  </content>
        |</idea-plugin>
        """.trimMargin())
  }

  private fun generateProductModulesWithPlugin(): Path = 
    productModulesWithPlugins(plugins = listOf("plugin")).generateInTempDir().resolve(FILE_NAME)

  private fun productModulesWithPlugins(plugins: List<String>) = directoryContent {
      xml(FILE_NAME, """
            <product-modules>
              <bundled-plugins>
                ${plugins.joinToString("\n") { "<module>$it</module>" }}
              </bundled-plugins>  
            </product-modules>
          """.trimIndent())
    }
}