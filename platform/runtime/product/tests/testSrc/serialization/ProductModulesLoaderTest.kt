// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.serialization

import com.intellij.platform.runtime.product.ProductMode
import com.intellij.platform.runtime.product.RuntimeModuleLoadingRule
import com.intellij.platform.runtime.product.impl.ServiceModuleMapping
import com.intellij.platform.runtime.repository.*
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.util.io.directoryContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
                                      RawRuntimeModuleDescriptor.create("util", emptyList(), emptyList()),
                                      RawRuntimeModuleDescriptor.create("root", emptyList(), listOf("util")),
                                      RawRuntimeModuleDescriptor.create("plugin", listOf("plugin"), emptyList()),
    )
    writePluginXmlWithModules(tempDirectory.rootPath / "plugin", "<idea-plugin><id>plugin</id></idea-plugin>")
    val xml = generateProductModulesWithPlugin()
    val productModules = ProductModulesSerialization.loadProductModules(xml, ProductMode.MONOLITH, repository)
    val mainGroupModules = productModules.mainModuleGroup.includedModules.sortedBy { it.moduleDescriptor.moduleId.stringId }
    assertEquals(2, mainGroupModules.size)
    val (root, util) = mainGroupModules
    assertEquals("root", root.moduleDescriptor.moduleId.stringId)
    assertEquals(RuntimeModuleLoadingRule.REQUIRED, root.loadingRule)
    assertEquals("util", util.moduleDescriptor.moduleId.stringId)
    assertEquals(RuntimeModuleLoadingRule.ON_DEMAND, util.loadingRule)
    assertEquals(emptySet<RuntimeModuleId>(), productModules.mainModuleGroup.optionalModuleIds)

    val pluginGroup = productModules.bundledPluginModuleGroups.single()
    assertEquals("plugin", pluginGroup.includedModules.single().moduleDescriptor.moduleId.stringId)
  }
  
  @Test
  fun `optional modules in main module group`() {
    val repository = createRepository(tempDirectory.rootPath,
                                      RawRuntimeModuleDescriptor.create("util", emptyList(), emptyList()),
                                      RawRuntimeModuleDescriptor.create("root", emptyList(), emptyList()),
                                      RawRuntimeModuleDescriptor.create("required", emptyList(), emptyList()),
                                      RawRuntimeModuleDescriptor.create("optional", emptyList(), listOf("root")),
    )
    val xml = directoryContent { 
      xml(FILE_NAME, """
        <product-modules>
          <main-root-modules>
            <module loading="embedded">root</module>
            <module loading="required">required</module>
            <module loading="optional">optional</module>
            <module loading="optional">unknown-optional</module>
          </main-root-modules>
        </product-modules>
      """.trimIndent())
    }.generateInTempDir().resolve(FILE_NAME)
    val productModules = ProductModulesSerialization.loadProductModules(xml, ProductMode.MONOLITH, repository)
    val mainGroupModules = productModules.mainModuleGroup.includedModules.sortedBy { it.moduleDescriptor.moduleId.stringId }
    assertEquals(3, mainGroupModules.size)
    val (optional, required, root) = mainGroupModules
    assertEquals("root", root.moduleDescriptor.moduleId.stringId)
    assertEquals(RuntimeModuleLoadingRule.EMBEDDED, root.loadingRule)
    assertEquals("required", required.moduleDescriptor.moduleId.stringId)
    assertEquals(RuntimeModuleLoadingRule.REQUIRED, required.loadingRule)
    assertEquals("optional", optional.moduleDescriptor.moduleId.stringId)
    assertEquals(RuntimeModuleLoadingRule.OPTIONAL, optional.loadingRule)
    assertEquals(setOf("optional", "unknown-optional"), productModules.mainModuleGroup.optionalModuleIds.mapTo(HashSet()) { it.stringId })
  }
  
  @Test
  fun `multiple modules in plugin module group`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor.create("root", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor.create("plugin", listOf("plugin"), emptyList()),
      RawRuntimeModuleDescriptor.create("optional", emptyList(), listOf("plugin")),
    )
    writePluginXmlWithModules(tempDirectory.rootPath / "plugin", "plugin", "optional", "unknown")

    val xml = generateProductModulesWithPlugin()
    val productModules = ProductModulesSerialization.loadProductModules(xml, ProductMode.MONOLITH, repository)
    val pluginModuleGroup = productModules.bundledPluginModuleGroups.single()
    val pluginModules = pluginModuleGroup.includedModules
    assertEquals(2, pluginModules.size)
    val (plugin, optional) = pluginModules
    assertEquals("plugin", plugin.moduleDescriptor.moduleId.stringId)
    assertEquals(RuntimeModuleLoadingRule.EMBEDDED, plugin.loadingRule)
    assertEquals("optional", optional.moduleDescriptor.moduleId.stringId)
    assertEquals(RuntimeModuleLoadingRule.OPTIONAL, optional.loadingRule)
    assertEquals(setOf("optional", "unknown"), pluginModuleGroup.optionalModuleIds.mapTo(HashSet()) { it.stringId })
  }
  
  @Test
  fun `enable plugin modules in relevant modes`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor.create("root", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor.create("intellij.platform.frontend.split", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor.create("intellij.platform.backend", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor.create("intellij.platform.monolith", emptyList(), listOf("intellij.platform.backend")),
      RawRuntimeModuleDescriptor.create("plugin", listOf("plugin"), emptyList()),
      RawRuntimeModuleDescriptor.create("plugin.common", emptyList(), listOf("plugin")),
      RawRuntimeModuleDescriptor.create("plugin.frontend", emptyList(), listOf("plugin", "intellij.platform.frontend.split")),
      RawRuntimeModuleDescriptor.create("plugin.localIde", emptyList(), listOf("plugin", "intellij.platform.monolith")),
    )
    writePluginXmlWithModules(tempDirectory.rootPath / "plugin", "plugin", "plugin.common", "plugin.frontend", "plugin.localIde")

    val xml = generateProductModulesWithPlugin()
    fun checkGroup(productMode: ProductMode, additionalModuleName: String) {
      val productModules = ProductModulesSerialization.loadProductModules(xml, productMode, repository)
      val pluginModuleGroup = productModules.bundledPluginModuleGroups.single()
      val pluginModules = pluginModuleGroup.includedModules
      assertEquals(3, pluginModules.size)
      val (plugin, common, additional) = pluginModules
      assertEquals("plugin", plugin.moduleDescriptor.moduleId.stringId)
      assertEquals("plugin.common", common.moduleDescriptor.moduleId.stringId)
      assertEquals(additionalModuleName, additional.moduleDescriptor.moduleId.stringId)
    }
    checkGroup(ProductMode.MONOLITH, "plugin.localIde")
    checkGroup(ProductMode.FRONTEND, "plugin.frontend")
  }

  @Test
  fun inclusion() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor.create("root", listOf("root"), emptyList()),
      RawRuntimeModuleDescriptor.create("common.plugin", listOf("common.plugin"), emptyList()),
      RawRuntimeModuleDescriptor.create("additional", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor.create("plugin", listOf("plugin"), emptyList()),
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
            <main-root-modules>
              <module loading="required">additional</module>
            </main-root-modules>
            <bundled-plugins>
              <module>plugin</module>
            </bundled-plugins>  
          </product-modules>
        """.trimIndent())
    }.generateInTempDir().resolve(FILE_NAME)
    val productModules = ProductModulesSerialization.loadProductModules(xmlPath, ProductMode.FRONTEND, repository)
    val mainModules = productModules.mainModuleGroup.includedModules
    assertEquals(listOf("additional", "root"), mainModules.map { it.moduleDescriptor.moduleId.stringId })
    val bundledPlugins = productModules.bundledPluginModuleGroups.map { it.mainModule.moduleId.stringId }
    assertEquals(listOf("plugin", "common.plugin"), bundledPlugins)
  }
  
  @Test
  fun `inclusion without some modules`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor.create("root", listOf("root"), emptyList()),
      RawRuntimeModuleDescriptor.create("additional", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor.create("plugin", listOf("plugin"), emptyList()),
      RawRuntimeModuleDescriptor.create("plugin2", listOf("plugin2"), emptyList()),
    )
    writePluginXmlWithModules(tempDirectory.rootPath.resolve("plugin"), "plugin")
    writePluginXmlWithModules(tempDirectory.rootPath.resolve("plugin2"), "plugin2")
    val rootProductModulesPath = tempDirectory.rootPath.resolve("root/META-INF/root")
    productModulesWithPlugins(
      mainModules = listOf("root", "additional"),
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
    val productModules = ProductModulesSerialization.loadProductModules(xmlPath, ProductMode.FRONTEND, repository)
    val mainModules = productModules.mainModuleGroup.includedModules
    assertEquals(listOf("root"), mainModules.map { it.moduleDescriptor.moduleId.stringId })
    val bundledPlugins = productModules.bundledPluginModuleGroups.map { it.mainModule.moduleId.stringId }
    assertEquals(listOf("plugin"), bundledPlugins)
  }
  
  @Test
  fun `service module mapping`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor.create("root", listOf("root"), emptyList()),
      RawRuntimeModuleDescriptor.create("additional1", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor.create("lib.common", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor.create("plugin1", listOf("plugin1"), listOf("additional1", "lib.common")),
      RawRuntimeModuleDescriptor.create("plugin2", listOf("plugin2"), listOf("lib.common")),
    )
    writePluginXmlWithModules(tempDirectory.rootPath.resolve("plugin1"), "plugin1")
    writePluginXmlWithModules(tempDirectory.rootPath.resolve("plugin2"), "plugin2")
    val rootProductModulesPath = tempDirectory.rootPath.resolve("root/META-INF/root")
    productModulesWithPlugins(
      mainModules = listOf("root"),
      plugins = listOf("plugin1", "plugin2")
    ).generate(rootProductModulesPath)
    val productModules = ProductModulesSerialization.loadProductModules(rootProductModulesPath.resolve(FILE_NAME), ProductMode.MONOLITH, repository)
    val (plugin1, plugin2) = productModules.bundledPluginModuleGroups
    val moduleMapping = ServiceModuleMapping.buildMapping(productModules)
    assertEquals(listOf("additional1", "lib.common"), moduleMapping.getAdditionalModules(plugin1).map { it.moduleId.stringId})
    assertEquals(listOf("lib.common"), moduleMapping.getAdditionalModules(plugin2).map { it.moduleId.stringId })
  }
  
  @Test
  fun `service module mapping reports error about ambiguous module`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor.create("root", listOf("root"), emptyList()),
      RawRuntimeModuleDescriptor.create("additional", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor.create("plugin1", listOf("plugin1"), listOf("additional")),
      RawRuntimeModuleDescriptor.create("plugin2", listOf("plugin2"), listOf("additional")),
    )
    writePluginXmlWithModules(tempDirectory.rootPath.resolve("plugin1"), "plugin1")
    writePluginXmlWithModules(tempDirectory.rootPath.resolve("plugin2"), "plugin2")
    val rootProductModulesPath = tempDirectory.rootPath.resolve("root/META-INF/root")
    productModulesWithPlugins(
      mainModules = listOf("root"),
      plugins = listOf("plugin1", "plugin2")
    ).generate(rootProductModulesPath)
    val productModules = ProductModulesSerialization.loadProductModules(rootProductModulesPath.resolve(FILE_NAME), ProductMode.MONOLITH, repository)
    assertThrows<MalformedRepositoryException> {
      ServiceModuleMapping.buildMapping(productModules)
    }
  }

  private fun writePluginXmlWithModules(resourcePath: Path, pluginId: String, vararg contentModules: String) {
    writePluginXml(resourcePath, """
        |<idea-plugin>
        |  <id>$pluginId</id>
        |  <content>
        |    ${contentModules.joinToString("\n    ") { "<module name=\"$it\"/>"}}
        |  </content>
        |</idea-plugin>
        """.trimMargin())
  }

  private fun generateProductModulesWithPlugin(): Path = 
    productModulesWithPlugins(plugins = listOf("plugin")).generateInTempDir().resolve(FILE_NAME)

  private fun productModulesWithPlugins(mainModules: List<String> = listOf("root"), plugins: List<String>) = directoryContent {
      xml(FILE_NAME, """
            <product-modules>
              <main-root-modules>
               ${mainModules.joinToString("\n") { 
                  "<module loading=\"required\">$it</module>"
               }}
              </main-root-modules>
              <bundled-plugins>
                ${plugins.joinToString("\n") { "<module>$it</module>" }}
              </bundled-plugins>  
            </product-modules>
          """.trimIndent())
    }
}