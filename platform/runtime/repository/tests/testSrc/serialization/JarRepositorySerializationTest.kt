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

class JarRepositorySerializationTest {
  @JvmField
  @RegisterExtension
  val tempDirectory = TempDirectoryExtension()

  @Test
  fun `empty module`() {
    check(listOf(RawRuntimeModuleDescriptor("ij.platform.util", emptyList(), emptyList()))) { 
      xml("ij.platform.util.xml", """
        <module name="ij.platform.util">
        </module>
      """.trimIndent())
    }
  }
  
  @Test
  fun `single module`() {
    check(listOf(RawRuntimeModuleDescriptor("ij.platform.util", listOf("ij-util.jar"), emptyList()))) {
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
      RawRuntimeModuleDescriptor("ij.platform.util.rt", listOf("ij-util-rt.jar"), emptyList()),
      RawRuntimeModuleDescriptor("ij.platform.util", emptyList(), listOf("ij.platform.util.rt")),
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

  private fun check(descriptors: List<RawRuntimeModuleDescriptor>, content: DirectoryContentBuilder.() -> Unit) {
    val jarFile = jarFile { 
      dir("META-INF") {
        file("MANIFEST.MF", """
          |Manifest-Version: 1.0
          |Specification-Title: ${JarFileSerializer.SPECIFICATION_TITLE}
          |Specification-Version: ${JarFileSerializer.SPECIFICATION_VERSION}
          |Implementation-Version: ${JarFileSerializer.SPECIFICATION_VERSION}.0
        """.trimMargin().replace("\n", "\r\n") + "\r\n\r\n")
      }
      content()
    }
    checkSaving(descriptors, jarFile)
    checkLoading(jarFile, descriptors)
  }

  private fun checkLoading(zipFileSpec: DirectoryContentSpec, expectedDescriptors: List<RawRuntimeModuleDescriptor>) {
    val actualDescriptors = RuntimeModuleRepositorySerialization.loadFromJar(zipFileSpec.generateInTempDir()).values
    UsefulTestCase.assertSameElements(actualDescriptors, expectedDescriptors)
  }

  private fun checkSaving(descriptors: List<RawRuntimeModuleDescriptor>, zipFileSpec: DirectoryContentSpec) {
    val jarFile = tempDirectory.rootPath.resolve("descriptors.jar")
    RuntimeModuleRepositorySerialization.saveToJar(descriptors, jarFile, 0)
    jarFile.assertMatches(zipFileSpec)
  }
}