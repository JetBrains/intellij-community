// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository

import com.intellij.openapi.application.PathManager
import com.intellij.platform.runtime.repository.RuntimeModuleId.raw
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor.create
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import com.intellij.project.IntelliJProjectConfiguration.Companion.getLocalMavenRepo
import com.intellij.testFramework.rules.TempDirectoryExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.RegisterExtension
import org.junitpioneer.jupiter.cartesian.CartesianTest
import java.nio.file.Path
import kotlin.io.path.Path

class RepositoryTest {
  @JvmField
  @RegisterExtension
  val tempDirectory = TempDirectoryExtension()

  @Test
  fun `resolved dependencies`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      createModuleDescriptor("ij.foo", emptyList(), emptyList()),
      createModuleDescriptor("ij.bar", emptyList(), listOf("ij.foo")),
    )
    val barId = moduleId("ij.bar")
    val bar = repository.getModule(barId)
    val fooId = moduleId("ij.foo")
    val foo = repository.getModule(fooId)
    assertEquals(listOf(foo), bar.dependencies)

    val fooHeader = repository.findModuleHeader(fooId)
    assertNotNull(fooHeader)
    assertEquals("ij.foo", fooHeader.moduleId.name)
    val barHeader = repository.findModuleHeader(barId)
    assertEquals(emptyList<RuntimeModuleId>(), fooHeader.dependencies)
    assertNotNull(barHeader)
    assertEquals("ij.bar", barHeader.moduleId.name)
    assertEquals(listOf(fooId), barHeader.dependencies)
  }

  @Test
  fun `unresolved dependency`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      createModuleDescriptor("ij.foo", emptyList(), emptyList()),
      createModuleDescriptor("ij.bar", emptyList(), listOf("ij.foo", "unresolved")),
      createModuleDescriptor("ij.baz", emptyList(), listOf("ij.bar")),
    )
    fun RuntimeModuleId.assertUnresolved(vararg pathToFailed: RuntimeModuleId) {
      val result = repository.resolveModule(this)
      assertNull(result.resolvedModule)
      assertEquals(pathToFailed.toList(), result.failedDependencyPath)
    }
    val unresolvedId = moduleId("unresolved")
    val barId = moduleId("ij.bar")
    val bazId = moduleId("ij.baz")
    unresolvedId.assertUnresolved(unresolvedId)
    barId.assertUnresolved(barId, unresolvedId)
    bazId.assertUnresolved(bazId, barId, unresolvedId)
    
    val exception = assertThrows(MalformedRepositoryException::class.java) {
      repository.getModule(bazId)
    }
    assertEquals("Cannot resolve module 'ij.baz': module 'unresolved' (<- 'ij.bar' <- 'ij.baz') is not found", exception.message)
    assertEquals("ij.bar", repository.findModuleHeader(barId)?.moduleId?.name)
  }

  @Test
  fun `circular dependency`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      createModuleDescriptor("ij.foo", emptyList(), listOf("ij.bar")),
      createModuleDescriptor("ij.bar", emptyList(), listOf("ij.foo")),
      createModuleDescriptor("ij.baz", emptyList(), listOf("ij.bar")),
    )
    val baz = repository.getModule(moduleId("ij.baz"))
    val bar = repository.getModule(moduleId("ij.bar"))
    val foo = repository.getModule(moduleId("ij.foo"))
    assertEquals(listOf(bar), baz.dependencies)
    assertEquals(listOf(foo), bar.dependencies)
    assertEquals(listOf(bar), foo.dependencies)
  }

  @Test
  fun `relative path`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      createModuleDescriptor("ij.foo", listOf("foo.jar"), emptyList()),
      createModuleDescriptor("ij.bar", listOf("../bar/bar.jar"), emptyList()),
    )
    val foo = repository.getModule(moduleId("ij.foo"))
    assertEquals(listOf(tempDirectory.rootPath.resolve("foo.jar")), foo.resourceRootPaths)
    val bar = repository.getModule(moduleId("ij.bar"))
    assertEquals(listOf(tempDirectory.rootPath.parent.resolve("bar/bar.jar")), bar.resourceRootPaths)
  }
  
  @Test
  fun `compute resource paths without resolving`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      createModuleDescriptor("ij.foo", listOf("foo.jar"), listOf("unresolved")),
    )
    assertEquals(listOf(tempDirectory.rootPath.resolve("foo.jar")), 
                 repository.getModuleResourcePaths(moduleId("ij.foo")))
  }

  @Test
  fun `resource path macros`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      createModuleDescriptor("ij.foo", listOf($$"$PROJECT_DIR$/foo.jar"), emptyList()),
      createModuleDescriptor("ij.bar", listOf("${getLocalMavenRepo()}/bar/bar.jar"), emptyList()),
    )
    
    //ensure that tempDirectory will be treated as the project root if 'idea.home.path' isn't specified explicitly
    tempDirectory.newFile("intellij.idea.community.main.iml")
    tempDirectory.newDirectory(".idea")
    
    val foo = repository.getModule(moduleId("ij.foo"))
    val fooJarPath = foo.resourceRootPaths.single()
    //$PROJECT_DIR macro may be resolved differently depending on whether 'idea.home.path' property is specified or not 
    val possibleExpectedPaths = setOf(
      Path(PathManager.getHomePath(), "foo.jar"), 
      tempDirectory.rootPath.resolve("foo.jar")
    )
    assertTrue(fooJarPath in possibleExpectedPaths, "$fooJarPath is not in $possibleExpectedPaths")
    
    val bar = repository.getModule(moduleId("ij.bar"))
    assertEquals(listOf(getLocalMavenRepo().resolve("bar/bar.jar")), bar.resourceRootPaths)
  }

  @Test
  fun `invalid macro usage`() {
    val incorrectPaths = listOf(
      $$"$UNKNOWN_MACRO$/foo.jar",
      $$"$PROJECT_DIR$-foo.jar",
      $$"$PROJECT_DIR$/../foo.jar",
    )
    for (path in incorrectPaths) {
      val repository = createRepository(
        tempDirectory.rootPath,
        createModuleDescriptor("ij.foo", listOf(path), emptyList())
      )
      val module = repository.getModule(moduleId("ij.foo"))
      assertThrows(MalformedRepositoryException::class.java, { module.resourceRootPaths }, "Path $path is incorrect")
    }
  }

  @Test
  fun `module classpath`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      createModuleDescriptor("ij.foo", listOf("foo.jar"), emptyList()),
      createModuleDescriptor("ij.bar", listOf("bar.jar"), listOf("ij.foo")),
      createModuleDescriptor("ij.baz", listOf("baz.jar"), listOf("ij.foo")),
      createModuleDescriptor("ij.main", emptyList(), listOf("ij.bar", "ij.baz")),
    )
    val classpath = repository.getModule(moduleId("ij.main")).moduleClasspath
    assertEquals(listOf("bar.jar", "foo.jar", "baz.jar").map { tempDirectory.rootPath.resolve(it) }, classpath)
  }

  @CartesianTest(name = "stored bootstrap module = {0}, loadFromCompact = {1}")
  fun `bootstrap classpath`(
    @CartesianTest.Values(strings = ["", "ij.foo", "ij.bar"]) storedBootstrapModule: String, 
    @CartesianTest.Values(booleans = [true, false]) loadFromCompact: Boolean
  ) {
    val fooId = raw("ij.foo", RuntimeModuleId.LEGACY_JPS_MODULE_NAMESPACE_SUFFIX)
    val descriptors = arrayOf(
      create(fooId, listOf("foo.jar"), emptyList()),
      create(raw("ij.bar", RuntimeModuleId.LEGACY_JPS_MODULE_NAMESPACE_SUFFIX), listOf("bar.jar"),
      listOf(fooId)),
    )
    val basePath = tempDirectory.rootPath
    val bootstrapModuleName = storedBootstrapModule.takeIf { it.isNotEmpty() }
    val filePath: Path
    if (loadFromCompact) {
      filePath = basePath.resolve("module-descriptors.dat")
      RuntimeModuleRepositorySerialization.saveToCompactFile(descriptors.asList(), emptyList(), bootstrapModuleName, filePath, 0)
    }
    else {
      filePath = basePath.resolve("module-descriptors.jar")
      RuntimeModuleRepositorySerialization.saveToJar(descriptors.asList(), emptyList(), bootstrapModuleName, filePath, 0)
    }
    val repository = RuntimeModuleRepository.create(filePath)
    assertEquals(listOf(basePath.resolve("bar.jar"), basePath.resolve("foo.jar")), repository.getBootstrapClasspath("ij.bar"))
  }

  private fun moduleId(moduleName: String): RuntimeModuleId = raw(moduleName, RuntimeModuleId.DEFAULT_NAMESPACE)
}