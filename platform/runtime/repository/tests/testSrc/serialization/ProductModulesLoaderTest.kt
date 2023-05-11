// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization

import com.intellij.platform.runtime.repository.ModuleImportance
import com.intellij.platform.runtime.repository.createRepository
import com.intellij.platform.runtime.repository.xml
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.util.io.directoryContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private const val FILE_NAME = "product-modules.xml"

class ProductModulesLoaderTest {
  @JvmField
  @RegisterExtension
  val tempDirectory = TempDirectoryExtension()

  @Test
  fun simple() {
    val repository = createRepository(tempDirectory.rootPath, 
      RawRuntimeModuleDescriptor("root", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor("plugin", emptyList(), emptyList()),
    )
    val xml = directoryContent { 
      xml(FILE_NAME, """
        <product-modules>
          <root-platform-modules>
            <module importance="functional">root</module>
          </root-platform-modules>
          <bundled-plugins>
            <module>plugin</module>
          </bundled-plugins>  
        </product-modules>
      """.trimIndent())
    }.generateInTempDir().resolve(FILE_NAME)
    val productModules = RuntimeModuleRepositorySerialization.loadProductModules(xml, repository)
    val platformModule = productModules.rootPlatformModules.single()
    assertEquals("root", platformModule.moduleDescriptor.moduleId.stringId)
    assertEquals(ModuleImportance.FUNCTIONAL, platformModule.importance)
    val pluginModule = productModules.bundledPluginMainModules.single()
    assertEquals("plugin", pluginModule.moduleId.stringId)
  }
}