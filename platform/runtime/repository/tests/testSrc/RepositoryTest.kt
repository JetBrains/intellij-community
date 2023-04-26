// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository

import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.testFramework.rules.TempDirectoryExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path

class RepositoryTest {
  @JvmField
  @RegisterExtension
  val tempDirectory = TempDirectoryExtension()

  @Test
  fun dependencies() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor("ij.platform.util.rt", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor("ij.platform.util", emptyList(), listOf("ij.platform.util.rt")),
    )
    val util = repository.getModule(RuntimeModuleId.module("ij.platform.util"))
    val utilRt = repository.getModule(RuntimeModuleId.module("ij.platform.util.rt"))
    assertEquals(listOf(utilRt), util.dependencies)
  }

  @Test
  fun `relative path`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor("ij.foo", listOf("foo.jar"), emptyList()),
      RawRuntimeModuleDescriptor("ij.bar", listOf("../bar/bar.jar"), emptyList()),
    )
    val foo = repository.getModule(RuntimeModuleId.module("ij.foo"))
    assertEquals(listOf(tempDirectory.rootPath.resolve("foo.jar")), foo.resourceRootPaths)
    val bar = repository.getModule(RuntimeModuleId.module("ij.bar"))
    assertEquals(listOf(tempDirectory.rootPath.parent.resolve("bar/bar.jar")), bar.resourceRootPaths)
  }

  @Test
  fun `resource path macros`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor("ij.foo", listOf("\$PROJECT_DIR$/foo.jar"), emptyList()),
      RawRuntimeModuleDescriptor("ij.bar", listOf("\$MAVEN_REPOSITORY$/bar/bar.jar"), emptyList()),
    )
    val foo = repository.getModule(RuntimeModuleId.module("ij.foo"))
    assertEquals(listOf(Path.of("foo.jar").toAbsolutePath()), foo.resourceRootPaths)
    val bar = repository.getModule(RuntimeModuleId.module("ij.bar"))
    assertEquals(listOf(IntelliJProjectConfiguration.getLocalMavenRepo().resolve("bar/bar.jar")), bar.resourceRootPaths)
  }

  @Test
  fun `invalid macro usage`() {
    val incorrectPaths = listOf(
      "\$UNKNOWN_MACRO$/foo.jar",
      "\$PROJECT_DIR$-foo.jar",
      "\$PROJECT_DIR$/../foo.jar",
    )
    for (path in incorrectPaths) {
      assertThrows(MalformedRepositoryException::class.java, {
        createRepository(
          tempDirectory.rootPath,
          RawRuntimeModuleDescriptor("ij.foo", listOf(path), emptyList())
        )
      }, "Path $path is incorrect")
    }
  }
}