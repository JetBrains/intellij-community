// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization

import com.intellij.platform.runtime.repository.serialization.impl.JarFileSerializer
import com.intellij.platform.runtime.repository.xml
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.util.io.DirectoryContentBuilder
import com.intellij.util.io.DirectoryContentSpec
import com.intellij.util.io.assertMatches
import com.intellij.util.io.jarFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path

class JarRepositorySerializationTest {
  @JvmField
  @RegisterExtension
  val tempDirectory = TempDirectoryExtension()

  @Test
  fun `empty module`() {
    check(listOf(RawRuntimeModuleDescriptor.create("ij.platform.util", emptyList(), emptyList()))) { 
      xml("ij.platform.util.xml", """
        <module name="ij.platform.util">
        </module>
      """.trimIndent())
    }
  }
  
  @Test
  fun `single module`() {
    check(listOf(RawRuntimeModuleDescriptor.create("ij.platform.util", listOf("ij-util.jar"), emptyList()))) {
      xml("ij.platform.util.xml", """
          <module name="ij.platform.util">
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
      RawRuntimeModuleDescriptor.create("ij.platform.util.rt", listOf("ij-util-rt.jar"), emptyList()),
      RawRuntimeModuleDescriptor.create("ij.platform.util", emptyList(), listOf("ij.platform.util.rt")),
    )) {
      xml("ij.platform.util.xml", """
          <module name="ij.platform.util">
            <dependencies>
              <module name="ij.platform.util.rt"/>
            </dependencies>
          </module>
        """.trimIndent())
      xml("ij.platform.util.rt.xml", """
          <module name="ij.platform.util.rt">
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
      RawRuntimeModuleDescriptor.create("foo", listOf("foo.jar"), emptyList()),
      RawRuntimeModuleDescriptor.create("bar", listOf("bar.jar"), listOf("foo")),
    ), "bar", "bar.jar foo.jar") {
      xml("foo.xml", """
          <module name="foo">
            <resources>
              <resource-root path="foo.jar"/>
            </resources>
          </module>
        """.trimIndent())
      xml("bar.xml", """
          <module name="bar">
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
      RawRuntimeModuleDescriptor.create("ij.foo", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor.create("ij.bar", emptyList(), listOf("ij.foo", "unresolved")),
    )
    check(descriptors) {
      xml("ij.foo.xml", """
          <module name="ij.foo">
          </module>
      """.trimIndent())
      xml("ij.bar.xml", """
        <module name="ij.bar">
          <dependencies>
            <module name="ij.foo"/>
            <module name="unresolved"/>
          </dependencies>
        </module>
      """.trimIndent())
    }
  }

  private fun check(descriptors: List<RawRuntimeModuleDescriptor>, bootstrapModuleName: String? = null,
                    bootstrapClassPath: String? = null, content: DirectoryContentBuilder.() -> Unit) {
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
    RuntimeModuleRepositorySerialization.saveToJar(descriptors, bootstrapModuleName, jarFilePath, 0)
    jarFilePath.assertMatches(jarFile)

    val compactFilePath = out.resolve("module-descriptors.dat")
    RuntimeModuleRepositorySerialization.saveToCompactFile(descriptors, bootstrapModuleName, compactFilePath, 0)
    checkLoadingFromCompactFile(compactFilePath, descriptors)
    checkLoadingFromJar(jarFile, descriptors)
  }

  private fun checkLoadingFromCompactFile(filePath: Path, expectedDescriptors: List<RawRuntimeModuleDescriptor>) {
    val repositoryData = RuntimeModuleRepositorySerialization.loadFromCompactFile(filePath)
    val actualDescriptors = repositoryData.allIds.map { repositoryData.findDescriptor(it)!! }
    UsefulTestCase.assertSameElements(actualDescriptors, expectedDescriptors)
  }

  private fun checkLoadingFromJar(zipFileSpec: DirectoryContentSpec, expectedDescriptors: List<RawRuntimeModuleDescriptor>) {
    val repositoryData = RuntimeModuleRepositorySerialization.loadFromJar(zipFileSpec.generateInTempDir())
    val actualDescriptors = repositoryData.allIds.map { repositoryData.findDescriptor(it)!! }
    UsefulTestCase.assertSameElements(actualDescriptors, expectedDescriptors)
  }
}