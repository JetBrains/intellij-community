// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.runtimeModuleRepository.jps.build

import com.intellij.devkit.runtimeModuleRepository.jps.build.RuntimeModuleRepositoryBuildConstants.JAR_REPOSITORY_FILE_NAME
import com.intellij.platform.runtime.repository.RuntimeModuleId
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization
import org.jetbrains.jps.builders.CompileScopeTestBuilder
import org.jetbrains.jps.builders.JpsBuildTestCase
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule

class RuntimeModuleRepositoryBuilderTest : JpsBuildTestCase() {
  override fun setUp() {
    super.setUp()
    addModule(MARKER_MODULE, withTests = false)
    JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(myProject).outputUrl = getUrl("out")
  }

  fun `test module with tests`() {
    addModule("a", withTests = true)
    buildAndCheck { 
      descriptor("a")
      testDescriptor("a.tests", "a")
    }
  }
  
  fun `test module without sources`() {
    addModule("a", withTests = false, withSources = false)
    buildAndCheck { 
      descriptor("a", resourceDirName = null)
    }
  }
  
  fun `test module with resources only`() {
    val module = addModule("a", withTests = false, withSources = false)
    module.addSourceRoot(getUrl("a/res"), JavaResourceRootType.RESOURCE)
    buildAndCheck { 
      descriptor("a")
    }
  }

  fun `test dependency`() {
    val a = addModule("a", withTests = false)
    addModule("b", a, withTests = false)
    buildAndCheck { 
      descriptor("a")
      descriptor("b", "a")
    }
  }
  
  fun `test transitive dependency`() {
    val a = addModule("a", withTests = false)
    val b = addModule("b", a, withTests = false)
    addModule("c", b, withTests = false)
    buildAndCheck { 
      descriptor("a")
      descriptor("b", "a")
      descriptor("c", "b")
    }
  }
  
  fun `test dependency with tests`() {
    val a = addModule("a", withTests = true)
    addModule("b", a, withTests = true)
    buildAndCheck { 
      descriptor("a")
      testDescriptor("a.tests", "a")
      descriptor("b", "a")
      testDescriptor("b.tests", "b", "a.tests")
    }
  }

  fun `test transitive dependency via module without tests`() {
    val a = addModule("a", withTests = true)
    val b = addModule("b", a, withTests = false)
    addModule("c", b, withTests = true)
    buildAndCheck {
      descriptor("a")
      descriptor("b", "a")
      descriptor("c", "b")
      testDescriptor("a.tests", "a")
      testDescriptor("c.tests", "c", "b", "a.tests")
    }
  }

  fun `test circular dependency with tests`() {
    val a = addModule("a", withTests = true)
    val b = addModule("b", a, withTests = true)
    val dependency = a.dependenciesList.addModuleDependency(b)
    JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(dependency).scope = JpsJavaDependencyScope.RUNTIME
    buildAndCheck {
      descriptor("a", "b")
      testDescriptor("a.tests", "a", "b.tests")
      descriptor("b", "a")
      testDescriptor("b.tests", "b", "a.tests")
    }
  }
  
  fun `test circular dependency without tests`() {
    val a = addModule("a", withTests = false)
    val b = addModule("b", a, withTests = false)
    val dependency = a.dependenciesList.addModuleDependency(b)
    JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(dependency).scope = JpsJavaDependencyScope.RUNTIME
    addModule("c", b, withTests = true)
    buildAndCheck {
      descriptor("a", "b")
      descriptor("b", "a")
      descriptor("c", "b")
      testDescriptor("c.tests", "c", "b", "a")
    }
  }

  fun `test separate module for tests`() {
    addModule("a", withTests = false)
    addModule("a.tests", withTests = true, withSources = false)
    buildAndCheck {
      descriptor("a")
      testDescriptor("a.tests", "a", resourceDirName = "a.tests")
    }
  }

  private fun buildRepository(): Map<String, RawRuntimeModuleDescriptor> {
    doBuild(CompileScopeTestBuilder.make().targetTypes(RuntimeModuleRepositoryTarget)).assertSuccessful()
    val zipPath = orCreateProjectDir.toPath().resolve("out/${JAR_REPOSITORY_FILE_NAME}")
    return RuntimeModuleRepositorySerialization.loadFromJar(zipPath)
  }

  private fun addModule(name: String, vararg dependencies: JpsModule, withTests: Boolean, withSources: Boolean = true): JpsModule {
    val module = addModule(name, emptyArray(), null, null, jdk)
    if (withSources) {
      module.addSourceRoot(getUrl("$name/src"), JavaSourceRootType.SOURCE)
    }
    if (withTests) {
      module.addSourceRoot(getUrl("$name/testSrc"), JavaSourceRootType.TEST_SOURCE)
    }
    for (dependency in dependencies) {
      module.dependenciesList.addModuleDependency(dependency)
    }
    return module
  }
  
  private fun buildAndCheck(expected: RawDescriptorListBuilder.() -> Unit) {
    val actual = buildRepository().filterKeys { it != MARKER_MODULE && it != "${MARKER_MODULE}${RuntimeModuleId.TESTS_NAME_SUFFIX}" }
    val builder = RawDescriptorListBuilder()
    builder.expected()
    assertSameElements(actual.keys, builder.descriptors.map { it.id })
    for (expectedDescriptor in builder.descriptors) {
      assertEquals("Different data for '${expectedDescriptor.id}'.", expectedDescriptor, actual[expectedDescriptor.id]!!)
    }
  }
  
  private class RawDescriptorListBuilder {
    val descriptors = ArrayList<RawRuntimeModuleDescriptor>()
    
    fun descriptor(id: String, vararg dependencies: String, resourceDirName: String? = id) {
      val resources = if (resourceDirName != null) listOf("production/$resourceDirName") else emptyList()
      descriptor(id, resources, dependencies.asList())
    }

    fun testDescriptor(id: String, vararg dependencies: String, resourceDirName: String = id.removeSuffix(RuntimeModuleId.TESTS_NAME_SUFFIX)) {
      if (RuntimeModuleRepositoryBuilder.GENERATE_DESCRIPTORS_FOR_TEST_MODULES) {
        descriptor(id, listOf("test/$resourceDirName"), dependencies.asList())
      }
    }

    fun descriptor(id: String, resources: List<String>, dependencies: List<String>) {
      descriptors.add(RawRuntimeModuleDescriptor(id, resources, dependencies))
    }
  }
}

private const val MARKER_MODULE = "intellij.idea.community.main"
