// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization

import com.intellij.platform.runtime.repository.ModuleImportance
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.createRepository
import com.intellij.platform.runtime.repository.serialization.impl.PluginXmlReader
import com.intellij.platform.runtime.repository.writePluginXml
import com.intellij.testFramework.rules.TempDirectoryExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.io.path.div

class PluginXmlReaderTest {
  @JvmField
  @RegisterExtension
  val tempDirectory = TempDirectoryExtension()

  @Test
  fun `single module`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor("plugin.main", listOf("plugin"), emptyList()),
    )
    writePluginXml(tempDirectory.rootPath / "plugin", 
        """
            <idea-plugin>
              <id>plugin</id>
            </idea-plugin>  
        """.trimIndent()
    )
    val pluginModules = PluginXmlReader.loadPluginModules(repository.getModule(RuntimeModuleId.raw("plugin.main")), repository)
    val main = pluginModules.single()
    assertEquals("plugin.main", main.moduleId.stringId)
    assertEquals(ModuleImportance.FUNCTIONAL, main.importance)
  }
  
  @Test
  fun `multiple modules`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor("plugin.main", listOf("plugin"), emptyList()),
      RawRuntimeModuleDescriptor("plugin.optional", emptyList(), listOf("plugin.main")),
    )
    @Suppress("XmlUnusedNamespaceDeclaration") 
    writePluginXml(tempDirectory.rootPath / "plugin", 
       """
            <idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude" package="plugin">
              <name>Plugin</name>
              <id>plugin</id>
              <vendor>JetBrains</vendor>
              <description><![CDATA[Provides support]]></description>
              <dependencies>
                <plugin id="com.intellij.modules.lang"/>
              </dependencies>
              <content>
                <module name="plugin.main/subpackage"/>
                <module name="plugin.optional"/>
                <module name="plugin.unknown"/>
              </content>
            </idea-plugin>  
        """.trimIndent()
    )
    val pluginModules = PluginXmlReader.loadPluginModules(repository.getModule(RuntimeModuleId.raw("plugin.main")), repository)
    assertEquals(3, pluginModules.size)
    val (main, optional, unknown) = pluginModules
    assertEquals("plugin.main", main.moduleId.stringId)
    assertEquals(ModuleImportance.FUNCTIONAL, main.importance)
    assertEquals("plugin.optional", optional.moduleId.stringId)
    assertEquals(ModuleImportance.OPTIONAL, optional.importance)
    assertEquals("plugin.unknown", unknown.moduleId.stringId)
    assertEquals(ModuleImportance.OPTIONAL, unknown.importance)
  }
}