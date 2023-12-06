// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository

import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.testFramework.rules.TempDirectoryExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

class RepositoryTest {
  @JvmField
  @RegisterExtension
  val tempDirectory = TempDirectoryExtension()

  @Test
  fun `resolved dependencies`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor("ij.foo", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor("ij.bar", emptyList(), listOf("ij.foo")),
    )
    val bar = repository.getModule(RuntimeModuleId.raw("ij.bar"))
    val foo = repository.getModule(RuntimeModuleId.raw("ij.foo"))
    assertEquals(listOf(foo), bar.dependencies)
  }

  @Test
  fun `unresolved dependency`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor("ij.foo", emptyList(), emptyList()),
      RawRuntimeModuleDescriptor("ij.bar", emptyList(), listOf("ij.foo", "unresolved")),
      RawRuntimeModuleDescriptor("ij.baz", emptyList(), listOf("ij.bar")),
    )
    fun RuntimeModuleId.assertUnresolved(vararg pathToFailed: RuntimeModuleId) {
      val result = repository.resolveModule(this)
      assertNull(result.resolvedModule)
      assertEquals(pathToFailed.toList(), result.failedDependencyPath)
    }
    val unresolvedId = RuntimeModuleId.raw("unresolved")
    val barId = RuntimeModuleId.raw("ij.bar")
    val bazId = RuntimeModuleId.raw("ij.baz")
    unresolvedId.assertUnresolved(unresolvedId)
    barId.assertUnresolved(barId, unresolvedId)
    bazId.assertUnresolved(bazId, barId, unresolvedId)
    
    val exception = assertThrows(MalformedRepositoryException::class.java) {
      repository.getModule(bazId)
    }
    assertEquals("Cannot resolve module 'ij.baz': module 'unresolved' (<- 'ij.bar' <- 'ij.baz') is not found", exception.message)
  }

  @Test
  fun `circular dependency`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor("ij.foo", emptyList(), listOf("ij.bar")),
      RawRuntimeModuleDescriptor("ij.bar", emptyList(), listOf("ij.foo")),
      RawRuntimeModuleDescriptor("ij.baz", emptyList(), listOf("ij.bar")),
    )
    val baz = repository.getModule(RuntimeModuleId.raw("ij.baz"))
    val bar = repository.getModule(RuntimeModuleId.raw("ij.bar"))
    val foo = repository.getModule(RuntimeModuleId.raw("ij.foo"))
    assertEquals(listOf(bar), baz.dependencies)
    assertEquals(listOf(foo), bar.dependencies)
    assertEquals(listOf(bar), foo.dependencies)
  }

  @Test
  fun `relative path`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor("ij.foo", listOf("foo.jar"), emptyList()),
      RawRuntimeModuleDescriptor("ij.bar", listOf("../bar/bar.jar"), emptyList()),
    )
    val foo = repository.getModule(RuntimeModuleId.raw("ij.foo"))
    assertEquals(listOf(tempDirectory.rootPath.resolve("foo.jar")), foo.resourceRootPaths)
    val bar = repository.getModule(RuntimeModuleId.raw("ij.bar"))
    assertEquals(listOf(tempDirectory.rootPath.parent.resolve("bar/bar.jar")), bar.resourceRootPaths)
  }
  
  @Test
  fun `compute resource paths without resolving`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor("ij.foo", listOf("foo.jar"), listOf("unresolved")),
    )
    assertEquals(listOf(tempDirectory.rootPath.resolve("foo.jar")), 
                 repository.getModuleResourcePaths(RuntimeModuleId.raw("ij.foo")))
  }

  @Test
  fun `resource path macros`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor("ij.foo", listOf("\$PROJECT_DIR$/foo.jar"), emptyList()),
      RawRuntimeModuleDescriptor("ij.bar", listOf("\$MAVEN_REPOSITORY$/bar/bar.jar"), emptyList()),
    )
    val foo = repository.getModule(RuntimeModuleId.raw("ij.foo"))
    assertEquals(listOf(Path.of("foo.jar").toAbsolutePath()), foo.resourceRootPaths)
    val bar = repository.getModule(RuntimeModuleId.raw("ij.bar"))
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
      val repository = createRepository(
        tempDirectory.rootPath,
        RawRuntimeModuleDescriptor("ij.foo", listOf(path), emptyList())
      )
      val module = repository.getModule(RuntimeModuleId.raw("ij.foo"))
      assertThrows(MalformedRepositoryException::class.java, { module.resourceRootPaths }, "Path $path is incorrect")
    }
  }

  @Test
  fun `module classpath`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      RawRuntimeModuleDescriptor("ij.foo", listOf("foo.jar"), emptyList()),
      RawRuntimeModuleDescriptor("ij.bar", listOf("bar.jar"), listOf("ij.foo")),
      RawRuntimeModuleDescriptor("ij.baz", listOf("baz.jar"), listOf("ij.foo")),
      RawRuntimeModuleDescriptor("ij.main", emptyList(), listOf("ij.bar", "ij.baz")),
    )
    val classpath = repository.getModule(RuntimeModuleId.raw("ij.main")).moduleClasspath
    assertEquals(listOf("bar.jar", "foo.jar", "baz.jar").map { tempDirectory.rootPath.resolve(it) }, classpath)
  }

  @ParameterizedTest(name = "stored bootstrap module = {0}")
  @ValueSource(strings = ["", "ij.foo", "ij.bar"])
  fun `bootstrap classpath`(storedBootstrapModule: String) {
    val descriptors = arrayOf(
      RawRuntimeModuleDescriptor("ij.foo", listOf("foo.jar"), emptyList()),
      RawRuntimeModuleDescriptor("ij.bar", listOf("bar.jar"), listOf("ij.foo")),
    )
    val basePath = tempDirectory.rootPath
    val repository = createRepository(basePath, *descriptors, bootstrapModuleName = storedBootstrapModule.takeIf { it.isNotEmpty() })
    assertEquals(listOf(basePath.resolve("bar.jar"), basePath.resolve("foo.jar")), repository.getBootstrapClasspath("ij.bar"))
  }
}