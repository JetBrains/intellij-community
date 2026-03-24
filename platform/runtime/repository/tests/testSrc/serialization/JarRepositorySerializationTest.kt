// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization

import com.intellij.platform.runtime.repository.RuntimeModuleId.raw
import com.intellij.platform.runtime.repository.RuntimeModuleLoadingRule
import com.intellij.platform.runtime.repository.RuntimeModuleVisibility
import com.intellij.platform.runtime.repository.createModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor.create
import com.intellij.platform.runtime.repository.serialization.impl.JarFileSerializer
import com.intellij.platform.runtime.repository.xml
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.util.io.DirectoryContentBuilder
import com.intellij.util.io.DirectoryContentSpec
import com.intellij.util.io.assertMatches
import com.intellij.util.io.jarFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path

class JarRepositorySerializationTest {
  @JvmField
  @RegisterExtension
  val tempDirectory = TempDirectoryExtension()

  @Test
  fun `empty module`() {
    check(listOf(createModuleDescriptor("ij.platform.util", emptyList(), emptyList()))) {
      xml("ij.platform.util.xml", """
        <module name="ij.platform.util" namespace="jetbrains" visibility="public">
        </module>
      """.trimIndent())
    }
  }
  
  @Test
  fun `single module`() {
    check(listOf(createModuleDescriptor("ij.platform.util", listOf("ij-util.jar"), emptyList()))) {
      xml("ij.platform.util.xml", """
          <module name="ij.platform.util" namespace="jetbrains" visibility="public">
            <resources>
              <resource-root path="ij-util.jar"/>
            </resources>
          </module>
        """.trimIndent())
    }
  }
  
  @Test
  fun `two modules`() {
    check(listOf(
      create(raw("ij.platform.util.rt", "custom"), listOf("ij-util-rt.jar"), emptyList()),
      create(raw("ij.platform.util"), RuntimeModuleVisibility.INTERNAL, emptyList(), listOf(raw("ij.platform.util.rt", "custom"))),
    )) {
      xml("ij.platform.util.xml", """
          <module name="ij.platform.util" namespace="jetbrains" visibility="internal">
            <dependencies>
              <module name="ij.platform.util.rt" namespace="custom"/>
            </dependencies>
          </module>
        """.trimIndent())
      xml("ij.platform.util.rt.xml", """
          <module name="ij.platform.util.rt" namespace="custom" visibility="public">
            <resources>
              <resource-root path="ij-util-rt.jar"/>
            </resources>
          </module>
        """.trimIndent())
    }
  }

  @Test
  fun `bootstrap module classpath`() {
    check(listOf(
      createModuleDescriptor("foo", listOf("foo.jar"), emptyList()),
      createModuleDescriptor("bar", listOf("bar.jar"), listOf("foo")),
    ), emptyList(), "bar", "bar.jar foo.jar") {
      xml("foo.xml", """
          <module name="foo" namespace="jetbrains" visibility="public">
            <resources>
              <resource-root path="foo.jar"/>
            </resources>
          </module>
        """.trimIndent())
      xml("bar.xml", """
          <module name="bar" namespace="jetbrains" visibility="public">
            <dependencies>
              <module name="foo"/>
            </dependencies>
            <resources>
              <resource-root path="bar.jar"/>
            </resources>
          </module>
        """.trimIndent())
    }
  }
  
  @Test
  fun `unresolved dependency`() {
    val descriptors = listOf(
      createModuleDescriptor("ij.foo", emptyList(), emptyList()),
      createModuleDescriptor("ij.bar", emptyList(), listOf("ij.foo", "unresolved")),
    )
    check(descriptors) {
      xml("ij.foo.xml", """
          <module name="ij.foo" namespace="jetbrains" visibility="public">
          </module>
      """.trimIndent())
      xml("ij.bar.xml", """
        <module name="ij.bar" namespace="jetbrains" visibility="public">
          <dependencies>
            <module name="ij.foo"/>
            <module name="unresolved"/>
          </dependencies>
        </module>
      """.trimIndent())
    }
  }

  @Test
  fun `plugin header`() {
    val descriptors = listOf(
      createModuleDescriptor("ij.plugin", emptyList(), emptyList()),
      createModuleDescriptor("ij.optional", emptyList(), emptyList()),
      createModuleDescriptor("ij.required", emptyList(), emptyList()),
      createModuleDescriptor("ij.embedded", emptyList(), emptyList()),
      createModuleDescriptor("ij.required.backend", emptyList(), emptyList()),
    )
    val pluginHeader = RawRuntimePluginHeader.create(
      "plugin.id",
      raw("ij.plugin"),
      listOf(
        RawIncludedRuntimeModule(raw("ij.optional"), RuntimeModuleLoadingRule.OPTIONAL, null),
        RawIncludedRuntimeModule(raw("ij.required"), RuntimeModuleLoadingRule.REQUIRED, null),
        RawIncludedRuntimeModule(raw("ij.embedded"), RuntimeModuleLoadingRule.EMBEDDED, null),
        RawIncludedRuntimeModule(raw("ij.on_demand"), RuntimeModuleLoadingRule.ON_DEMAND, null),
        RawIncludedRuntimeModule(raw("ij.required.backend"), RuntimeModuleLoadingRule.OPTIONAL, raw("intellij.platform.backend")),
      )
    )
    check(descriptors, listOf(pluginHeader)) {
      xml("ij.plugin.xml", """
        <module name="ij.plugin" namespace="jetbrains" visibility="public">
        </module>
      """.trimIndent())
      xml("ij.optional.xml", """
        <module name="ij.optional" namespace="jetbrains" visibility="public">
        </module>
      """.trimIndent())
      xml("ij.required.xml", """
        <module name="ij.required" namespace="jetbrains" visibility="public">
        </module>
      """.trimIndent())
      xml("ij.embedded.xml", """
        <module name="ij.embedded" namespace="jetbrains" visibility="public">
        </module>
      """.trimIndent())
      xml("ij.required.backend.xml", """
        <module name="ij.required.backend" namespace="jetbrains" visibility="public">
        </module>
      """.trimIndent())
      dir("plugins") {
        xml("plugin.id.xml", """
         <!-- The IDE doesn't use this file; it takes data from module-descriptors.dat instead -->
         <plugin id="plugin.id">
           <plugin-descriptor-module name="ij.plugin" namespace="jetbrains"/>
           <module name="ij.optional" namespace="jetbrains" loading="optional"/>
           <module name="ij.required" namespace="jetbrains" loading="required"/>
           <module name="ij.embedded" namespace="jetbrains" loading="embedded"/>
           <module name="ij.on_demand" namespace="jetbrains" loading="on-demand"/>
           <module name="ij.required.backend" namespace="jetbrains" loading="optional" required-if-available="intellij.platform.backend"/>
         </plugin>
        """.trimIndent())
      }
    }
  }

  private fun check(
    moduleDescriptors: List<RawRuntimeModuleDescriptor>,
    pluginHeaders: List<RawRuntimePluginHeader> = emptyList(),
    bootstrapModuleName: String? = null,
    bootstrapClassPath: String? = null,
    content: DirectoryContentBuilder.() -> Unit,
  ) {
    val baseManifestText = """
          |Manifest-Version: 1.0
          |Specification-Title: ${JarFileSerializer.SPECIFICATION_TITLE}
          |Specification-Version: ${JarFileSerializer.SPECIFICATION_VERSION}
          |Implementation-Version: ${JarFileSerializer.SPECIFICATION_VERSION}.0
        """.trimMargin()
    val manifestText = if (bootstrapModuleName != null) {
      """
        |$baseManifestText
        |Bootstrap-Module-Name: $bootstrapModuleName
        |Bootstrap-Class-Path: $bootstrapClassPath
      """.trimMargin()
    }
    else baseManifestText
    val jarFile = jarFile { 
      dir("META-INF") {
        file("MANIFEST.MF", manifestText.replace("\n", "\r\n") + "\r\n\r\n")
      }
      content()
    }
    val out = tempDirectory.rootPath
    val jarFilePath = out.resolve("module-descriptors.jar")
    RuntimeModuleRepositorySerialization.saveToJar(moduleDescriptors, pluginHeaders, bootstrapModuleName, jarFilePath, 0)
    jarFilePath.assertMatches(jarFile)

    val compactFilePath = out.resolve("module-descriptors.dat")
    RuntimeModuleRepositorySerialization.saveToCompactFile(moduleDescriptors, pluginHeaders, bootstrapModuleName, compactFilePath, 0)
    checkLoadingFromCompactFile(compactFilePath, moduleDescriptors, pluginHeaders)
    checkLoadingFromJar(jarFile, moduleDescriptors)
  }

  private fun checkLoadingFromCompactFile(
    filePath: Path,
    expectedDescriptors: List<RawRuntimeModuleDescriptor>,
    expectedPluginHeaders: List<RawRuntimePluginHeader>
  ) {
    val repositoryData = RuntimeModuleRepositorySerialization.loadFromCompactFile(filePath)
    val actualDescriptors = repositoryData.allModuleIds.map { repositoryData.findDescriptor(it)!! }
    assertThat(actualDescriptors).containsExactlyInAnyOrderElementsOf(expectedDescriptors)

    val actualPluginHeaders = repositoryData.pluginHeaders
    assertThat(actualPluginHeaders.map { it.pluginId }).containsExactlyElementsOf(expectedPluginHeaders.map { it.pluginId })
    for (actualPluginHeader in actualPluginHeaders) {
      val expectedPluginHeader = expectedPluginHeaders.find { it.pluginId == actualPluginHeader.pluginId }!!
      assertThat(actualPluginHeader.pluginDescriptorModuleId).isEqualTo(expectedPluginHeader.pluginDescriptorModuleId)
      assertThat(actualPluginHeader.includedModules.map { it.moduleId }).containsExactlyElementsOf(expectedPluginHeader.includedModules.map { it.moduleId })
      for (actualIncludedModule in actualPluginHeader.includedModules) {
        val expectedIncludedModule = expectedPluginHeader.includedModules.find { it.moduleId == actualIncludedModule.moduleId }!!
        assertThat(actualIncludedModule.loadingRule).isEqualTo(expectedIncludedModule.loadingRule)
        assertThat(actualIncludedModule.requiredIfAvailableId).isEqualTo(expectedIncludedModule.requiredIfAvailableId)
      }
    }
  }

  private fun checkLoadingFromJar(zipFileSpec: DirectoryContentSpec, expectedDescriptors: List<RawRuntimeModuleDescriptor>) {
    val repositoryData = RuntimeModuleRepositorySerialization.loadFromJar(zipFileSpec.generateInTempDir())
    val actualDescriptors = repositoryData.allModuleIds.map { repositoryData.findDescriptor(it)!! }
    assertThat(actualDescriptors).containsExactlyInAnyOrderElementsOf(expectedDescriptors)
  }
}