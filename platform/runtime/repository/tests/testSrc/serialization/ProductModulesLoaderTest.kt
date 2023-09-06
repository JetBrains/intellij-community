// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization

import com.intellij.platform.runtime.repository.ModuleImportance
import com.intellij.platform.runtime.repository.createRepository
import com.intellij.platform.runtime.repository.writePluginXml
import com.intellij.platform.runtime.repository.xml
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.util.io.directoryContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
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
    val xml = directoryContent { 
      xml(FILE_NAME, """
        <product-modules>
          <main-root-modules>
            <module importance="functional">root</module>
          </main-root-modules>
          <bundled-plugins>
            <module>plugin</module>
          </bundled-plugins>  
        </product-modules>
      """.trimIndent())
    }.generateInTempDir().resolve(FILE_NAME)
    val productModules = RuntimeModuleRepositorySerialization.loadProductModules(xml, repository)
    val mainGroupModules = productModules.mainModuleGroup.includedModules.sortedBy { it.moduleDescriptor.moduleId.stringId }
    assertEquals(2, mainGroupModules.size)
    val (root, util) = mainGroupModules
    assertEquals("root", root.moduleDescriptor.moduleId.stringId)
    assertEquals(ModuleImportance.FUNCTIONAL, root.importance)
    assertEquals("util", util.moduleDescriptor.moduleId.stringId)
    assertEquals(ModuleImportance.SERVICE, util.importance)
    
    val pluginGroup = productModules.bundledPluginModuleGroups.single()
    assertEquals("plugin", pluginGroup.includedModules.single().moduleDescriptor.moduleId.stringId)
  }
}