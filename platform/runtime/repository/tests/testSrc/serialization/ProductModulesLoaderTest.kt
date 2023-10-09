// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization

import com.intellij.platform.runtime.repository.*
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.util.io.directoryContent
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
      RawRuntimeModuleDescriptor("util", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor("root", emptyList(), listOf("util")),
      RawRuntimeModuleDescriptor("plugin", listOf("plugin"), emptyList()),
    )
    writePluginXml(tempDirectory.rootPath / "plugin", "<idea-plugin><id>plugin</id></idea-plugin>")
    val xml = generateProductModulesWithPlugins("plugin")
    val productModules = RuntimeModuleRepositorySerialization.loadProductModules(xml, ProductMode.LOCAL_IDE, repository)
    val mainGroupModules = productModules.mainModuleGroup.includedModules.sortedBy { it.moduleDescriptor.moduleId.stringId }
    assertEquals(2, mainGroupModules.size)
    val (root, util) = mainGroupModules
    assertEquals("root", root.moduleDescriptor.moduleId.stringId)
    assertEquals(ModuleImportance.FUNCTIONAL, root.importance)
    assertEquals("util", util.moduleDescriptor.moduleId.stringId)
    assertEquals(ModuleImportance.SERVICE, util.importance)
    assertEquals(emptySet<RuntimeModuleId>(), productModules.mainModuleGroup.optionalModuleIds)

    val pluginGroup = productModules.bundledPluginModuleGroups.single()
    assertEquals("plugin", pluginGroup.includedModules.single().moduleDescriptor.moduleId.stringId)
  }
  
  @Test
  fun `optional modules in main module group`() {
    val repository = createRepository(tempDirectory.rootPath, 
      RawRuntimeModuleDescriptor("util", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor("root", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor("optional", emptyList(), listOf("root")),
    )
    val xml = directoryContent { 
      xml(FILE_NAME, """
        <product-modules>
          <main-root-modules>
            <module importance="functional">root</module>
            <module importance="optional">optional</module>
            <module importance="optional">unknown-optional</module>
          </main-root-modules>
        </product-modules>
      """.trimIndent())
    }.generateInTempDir().resolve(FILE_NAME)
    val productModules = RuntimeModuleRepositorySerialization.loadProductModules(xml, ProductMode.LOCAL_IDE, repository)
    val mainGroupModules = productModules.mainModuleGroup.includedModules.sortedBy { it.moduleDescriptor.moduleId.stringId }
    assertEquals(2, mainGroupModules.size)
    val (optional, root) = mainGroupModules
    assertEquals("root", root.moduleDescriptor.moduleId.stringId)
    assertEquals(ModuleImportance.FUNCTIONAL, root.importance)
    assertEquals("optional", optional.moduleDescriptor.moduleId.stringId)
    assertEquals(ModuleImportance.OPTIONAL, optional.importance)
    assertEquals(setOf("optional", "unknown-optional"), productModules.mainModuleGroup.optionalModuleIds.mapTo(HashSet()) { it.stringId })
  }
  
  @Test
  fun `multiple modules in plugin module group`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor("root", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor("plugin", listOf("plugin"), emptyList()),
      RawRuntimeModuleDescriptor("optional", emptyList(), listOf("plugin")),
    )
    writePluginXml(tempDirectory.rootPath / "plugin", """
      |<idea-plugin>
      |  <id>plugin</id>
      |  <content>
      |    <module name="optional"/>
      |    <module name="unknown"/>
      |  </content>
      |</idea-plugin>
      """.trimMargin())

    val xml = generateProductModulesWithPlugins("plugin")
    val productModules = RuntimeModuleRepositorySerialization.loadProductModules(xml, ProductMode.LOCAL_IDE, repository)
    val pluginModuleGroup = productModules.bundledPluginModuleGroups.single()
    val pluginModules = pluginModuleGroup.includedModules
    assertEquals(2, pluginModules.size)
    val (plugin, optional) = pluginModules
    assertEquals("plugin", plugin.moduleDescriptor.moduleId.stringId)
    assertEquals(ModuleImportance.FUNCTIONAL, plugin.importance)
    assertEquals("optional", optional.moduleDescriptor.moduleId.stringId)
    assertEquals(ModuleImportance.OPTIONAL, optional.importance)
    assertEquals(setOf("optional", "unknown"), pluginModuleGroup.optionalModuleIds.mapTo(HashSet()) { it.stringId })
  }
  
  @Test
  fun `enable plugin modules in relevant modes`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor("root", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor("intellij.platform.frontend", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor("intellij.platform.localIde", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor("plugin", listOf("plugin"), emptyList()),
      RawRuntimeModuleDescriptor("plugin.common", emptyList(), listOf("plugin")),
      RawRuntimeModuleDescriptor("plugin.frontend", emptyList(), listOf("plugin", "intellij.platform.frontend")),
      RawRuntimeModuleDescriptor("plugin.localIde", emptyList(), listOf("plugin", "intellij.platform.localIde")),
    )
    writePluginXml(tempDirectory.rootPath / "plugin", """
      |<idea-plugin>
      |  <id>plugin</id>
      |  <content>
      |    <module name="plugin.common"/>
      |    <module name="plugin.frontend"/>
      |    <module name="plugin.localIde"/>
      |  </content>
      |</idea-plugin>
      """.trimMargin())

    val xml = generateProductModulesWithPlugins("plugin")
    fun checkGroup(productMode: ProductMode, additionalModuleName: String) {
      val productModules = RuntimeModuleRepositorySerialization.loadProductModules(xml, productMode, repository)
      val pluginModuleGroup = productModules.bundledPluginModuleGroups.single()
      val pluginModules = pluginModuleGroup.includedModules
      assertEquals(3, pluginModules.size)
      val (plugin, common, additional) = pluginModules
      assertEquals("plugin", plugin.moduleDescriptor.moduleId.stringId)
      assertEquals("plugin.common", common.moduleDescriptor.moduleId.stringId)
      assertEquals(additionalModuleName, additional.moduleDescriptor.moduleId.stringId)
    }
    checkGroup(ProductMode.LOCAL_IDE, "plugin.localIde")
    checkGroup(ProductMode.FRONTEND, "plugin.frontend")
  }

  private fun generateProductModulesWithPlugins(vararg plugins: String): Path = directoryContent {
    xml(FILE_NAME, """
          <product-modules>
            <main-root-modules>
              <module importance="functional">root</module>
            </main-root-modules>
            <bundled-plugins>
              ${plugins.joinToString("\n") {
                 "<module>$it</module>"
              }}
            </bundled-plugins>  
          </product-modules>
        """.trimIndent())
  }.generateInTempDir().resolve(FILE_NAME)
}