// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository

import com.intellij.openapi.application.PathManager
import com.intellij.platform.runtime.repository.impl.RuntimeModuleRepositoryImpl
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import com.intellij.project.IntelliJProjectConfiguration.Companion.getLocalMavenRepo
import com.intellij.testFramework.rules.TempDirectoryExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
    val bar = repository.getModule(RuntimeModuleId.raw("ij.bar"))
    val foo = repository.getModule(RuntimeModuleId.raw("ij.foo"))
    assertEquals(listOf(foo), bar.dependencies)
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
      createModuleDescriptor("ij.foo", emptyList(), listOf("ij.bar")),
      createModuleDescriptor("ij.bar", emptyList(), listOf("ij.foo")),
      createModuleDescriptor("ij.baz", emptyList(), listOf("ij.bar")),
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
      createModuleDescriptor("ij.foo", listOf("foo.jar"), emptyList()),
      createModuleDescriptor("ij.bar", listOf("../bar/bar.jar"), emptyList()),
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
      createModuleDescriptor("ij.foo", listOf("foo.jar"), listOf("unresolved")),
    )
    assertEquals(listOf(tempDirectory.rootPath.resolve("foo.jar")), 
                 repository.getModuleResourcePaths(RuntimeModuleId.raw("ij.foo")))
  }

  @Test
  fun `resource path macros`() {
    val repository = createRepository(
      tempDirectory.rootPath,
      createModuleDescriptor("ij.foo", listOf("\$PROJECT_DIR$/foo.jar"), emptyList()),
      createModuleDescriptor("ij.bar", listOf("${getLocalMavenRepo()}/bar/bar.jar"), emptyList()),
    )
    
    //ensure that tempDirectory will be treated as the project root if 'idea.home.path' isn't specified explicitly
    tempDirectory.newFile("intellij.idea.community.main.iml")
    tempDirectory.newDirectory(".idea")
    
    val foo = repository.getModule(RuntimeModuleId.raw("ij.foo"))
    val fooJarPath = foo.resourceRootPaths.single()
    //$PROJECT_DIR macro may be resolved differently depending on whether 'idea.home.path' property is specified or not 
    val possibleExpectedPaths = setOf(
      Path(PathManager.getHomePath(), "foo.jar"), 
      tempDirectory.rootPath.resolve("foo.jar")
    )
    assertTrue(fooJarPath in possibleExpectedPaths, "$fooJarPath is not in $possibleExpectedPaths")
    
    val bar = repository.getModule(RuntimeModuleId.raw("ij.bar"))
    assertEquals(listOf(getLocalMavenRepo().resolve("bar/bar.jar")), bar.resourceRootPaths)
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
        createModuleDescriptor("ij.foo", listOf(path), emptyList())
      )
      val module = repository.getModule(RuntimeModuleId.raw("ij.foo"))
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
    val classpath = repository.getModule(RuntimeModuleId.raw("ij.main")).moduleClasspath
    assertEquals(listOf("bar.jar", "foo.jar", "baz.jar").map { tempDirectory.rootPath.resolve(it) }, classpath)
  }

  @CartesianTest(name = "stored bootstrap module = {0}, loadFromCompact = {1}")
  fun `bootstrap classpath`(
    @CartesianTest.Values(strings = ["", "ij.foo", "ij.bar"]) storedBootstrapModule: String, 
    @CartesianTest.Values(booleans = [true, false]) loadFromCompact: Boolean
  ) {
    val descriptors = arrayOf(
      createModuleDescriptor("ij.foo", listOf("foo.jar"), emptyList()),
      createModuleDescriptor("ij.bar", listOf("bar.jar"), listOf("ij.foo")),
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
  
  @Test
  fun `additional repositories`() {
    val main = createRepository(
      tempDirectory.rootPath,
      createModuleDescriptor("ij.foo", listOf("foo.jar"), emptyList()),
    ) as RuntimeModuleRepositoryImpl
    val additional1Path = tempDirectory.rootPath.resolve("additional1")
    val additional1 = createRawRepository(
      additional1Path,
      createModuleDescriptor("custom1.foo", listOf("custom1-foo.jar"), listOf("ij.foo")),
      createModuleDescriptor("custom1.bar", listOf("custom1-bar.jar"), listOf("custom1.foo")),
    )
    val additional2Path = tempDirectory.rootPath.resolve("additional2")
    val additional2 = createRawRepository(
      additional2Path,
      createModuleDescriptor("custom2", listOf("custom2.jar"), listOf("custom1.bar")),
    )
    main.loadAdditionalRepositories(listOf(additional1, additional2))
    val moduleId = RuntimeModuleId.raw("custom2")
    val classpath = main.getModule(moduleId).moduleClasspath
    assertEquals(listOf(
      additional2Path.resolve("custom2.jar"), 
      additional1Path.resolve("custom1-bar.jar"), 
      additional1Path.resolve("custom1-foo.jar"),
      tempDirectory.rootPath.resolve("foo.jar"),
    ), classpath)
    assertEquals(listOf(additional2Path.resolve("custom2.jar")), main.getModuleResourcePaths(moduleId))
  }

  private fun createRawRepository(basePath: Path, vararg descriptors: RawRuntimeModuleDescriptor): RawRuntimeModuleRepositoryData {
    return RawRuntimeModuleRepositoryData.create(descriptors.associateBy { it.moduleId }, emptyList(), basePath)
  }
}