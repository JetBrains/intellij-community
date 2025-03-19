// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.product.serialization

import com.intellij.platform.runtime.product.RuntimeModuleLoadingRule
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.createRepository
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.product.serialization.impl.loadPluginModules
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
      RawRuntimeModuleDescriptor.create("plugin.main", listOf("plugin"), emptyList()),
    )
    writePluginXml(tempDirectory.rootPath / "plugin", 
        """
            <idea-plugin>
              <id>plugin</id>
            </idea-plugin>  
        """.trimIndent()
    )
    val pluginModules = loadPluginModules(repository.getModule(RuntimeModuleId.raw("plugin.main")), repository, 
                                          ResourceFileResolver.createDefault(repository))
    val main = pluginModules.single()
    assertEquals("plugin.main", main.moduleId.stringId)
    assertEquals(RuntimeModuleLoadingRule.REQUIRED, main.loadingRule)
  }
  
  @Test
  fun `multiple modules`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor.create("plugin.main", listOf("plugin"), emptyList()),
      RawRuntimeModuleDescriptor.create("plugin.optional", emptyList(), listOf("plugin.main")),
      RawRuntimeModuleDescriptor.create("plugin.optional.explicit", emptyList(), listOf("plugin.main")),
      RawRuntimeModuleDescriptor.create("plugin.on_demand", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor.create("plugin.required", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor.create("plugin.embedded", emptyList(), emptyList()),
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
                <module name="plugin.optional.explicit" loading="optional"/>
                <module name="plugin.on_demand" loading="on-demand"/>
                <module name="plugin.required" loading="required"/>
                <module loading="embedded" name="plugin.embedded" />
                <module name="plugin.unknown"/>
              </content>
            </idea-plugin>  
        """.trimIndent()
    )
    val pluginModules = loadPluginModules(repository.getModule(RuntimeModuleId.raw("plugin.main")), repository, 
                                          ResourceFileResolver.createDefault(repository))
    assertEquals(7, pluginModules.size)
    val (main, optional, optionalExplicit, onDemand, required) = pluginModules
    val (embedded, unknown) = pluginModules.subList(5, 7)
    assertEquals("plugin.main", main.moduleId.stringId)
    assertEquals(RuntimeModuleLoadingRule.REQUIRED, main.loadingRule)
    assertEquals("plugin.optional", optional.moduleId.stringId)
    assertEquals(RuntimeModuleLoadingRule.OPTIONAL, optional.loadingRule)
    assertEquals("plugin.optional.explicit", optionalExplicit.moduleId.stringId)
    assertEquals(RuntimeModuleLoadingRule.OPTIONAL, optionalExplicit.loadingRule)
    assertEquals("plugin.on_demand", onDemand.moduleId.stringId)
    assertEquals(RuntimeModuleLoadingRule.ON_DEMAND, onDemand.loadingRule)
    assertEquals("plugin.required", required.moduleId.stringId)
    assertEquals(RuntimeModuleLoadingRule.REQUIRED, required.loadingRule)
    assertEquals("plugin.embedded", embedded.moduleId.stringId)
    assertEquals(RuntimeModuleLoadingRule.REQUIRED, embedded.loadingRule)
    assertEquals("plugin.unknown", unknown.moduleId.stringId)
    assertEquals(RuntimeModuleLoadingRule.OPTIONAL, unknown.loadingRule)
  }
}