// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.module

import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.moduleAssertion.DependencyAssertions
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class DependencyAssertionTest : ModuleAssertionTestCase() {

  @Test
  fun `test DependencyAssertions#assertDependencies`() {
    runBlocking {
      project.workspaceModel.update {
        addLibraryEntity("library1")
        addLibraryEntity("library2")
        addModuleEntity("project") {
          addModuleDependency("project.module1")
          addModuleDependency("project.module2")
          addLibraryDependency("library1")
          addLibraryDependency("library2")
        }
        addModuleEntity("project.module1") {
          addModuleDependency("project.module2")
          addLibraryDependency("library2")
        }
        addModuleEntity("project.module2")
      }
      ModuleAssertions.assertModuleEntity(project, "project") { module ->

        DependencyAssertions.assertDependencies(module, "project.module1", "project.module2", "library1", "library2")

        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertDependencies(module, emptyList())
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertDependencies(module, "project.module1", "project.module2")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertDependencies(module, "library1", "library2")
        }
      }
      ModuleAssertions.assertModuleEntity(project, "project.module1") { module ->

        DependencyAssertions.assertDependencies(module, "project.module2", "library2")

        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertDependencies(module, emptyList())
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertDependencies(module, "project.module2")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertDependencies(module, "library2")
        }
      }
      ModuleAssertions.assertModuleEntity(project, "project.module2") { module ->

        DependencyAssertions.assertDependencies(module, emptyList())

        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertDependencies(module, "project.module1", "project.module2")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertDependencies(module, "library1", "library2")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertDependencies(module, "project.module1", "project.module2", "library1", "library2")
        }
      }
    }
  }

  @Test
  fun `test DependencyAssertions#assertModuleDependencies`() {
    runBlocking {
      project.workspaceModel.update {
        addLibraryEntity("library1")
        addLibraryEntity("library2")
        addModuleEntity("project") {
          addModuleDependency("project.module1")
          addModuleDependency("project.module2")
          addLibraryDependency("library1")
          addLibraryDependency("library2")
        }
        addModuleEntity("project.module1") {
          addModuleDependency("project.module2")
          addLibraryDependency("library2")
        }
        addModuleEntity("project.module2")
      }
      ModuleAssertions.assertModuleEntity(project, "project") { module ->

        DependencyAssertions.assertModuleDependencies(module, "project.module1", "project.module2")

        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertModuleDependencies(module, emptyList())
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertModuleDependencies(module, "library1", "library2")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertModuleDependencies(module, "project.module1", "project.module2", "library1", "library2")
        }
      }
      ModuleAssertions.assertModuleEntity(project, "project.module1") { module ->

        DependencyAssertions.assertModuleDependencies(module, "project.module2")

        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertModuleDependencies(module, emptyList())
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertModuleDependencies(module, "library2")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertModuleDependencies(module, "library2", "project.module2")
        }
      }
      ModuleAssertions.assertModuleEntity(project, "project.module2") { module ->

        DependencyAssertions.assertModuleDependencies(module, emptyList())

        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertModuleDependencies(module, "library1", "library2")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertModuleDependencies(module, "project.module1", "project.module2")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertModuleDependencies(module, "library1", "library2", "project.module1", "project.module2")
        }
      }
    }
  }

  @Test
  fun `test DependencyAssertions#assertLibraryDependencies`() {
    runBlocking {
      project.workspaceModel.update {
        addLibraryEntity("library1")
        addLibraryEntity("library2")
        addModuleEntity("project") {
          addModuleDependency("project.module1")
          addModuleDependency("project.module2")
          addLibraryDependency("library1")
          addLibraryDependency("library2")
        }
        addModuleEntity("project.module1") {
          addModuleDependency("project.module2")
          addLibraryDependency("library2")
        }
        addModuleEntity("project.module2")
      }
      ModuleAssertions.assertModuleEntity(project, "project") { module ->

        DependencyAssertions.assertLibraryDependencies(module, "library1", "library2")

        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependencies(module, emptyList())
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependencies(module, "project.module1", "project.module2")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependencies(module, "library1", "library2", "project.module1", "project.module2")
        }
      }
      ModuleAssertions.assertModuleEntity(project, "project.module1") { module ->

        DependencyAssertions.assertLibraryDependencies(module, "library2")

        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependencies(module, emptyList())
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependencies(module, "library1", "library2")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependencies(module, "project.module2")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependencies(module, "library2", "project.module2")
        }
      }
      ModuleAssertions.assertModuleEntity(project, "project.module2") { module ->

        DependencyAssertions.assertLibraryDependencies(module, emptyList())

        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependencies(module, "library1", "library2")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependencies(module, "project.module1", "project.module2")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependencies(module, "library1", "library2", "project.module1", "project.module2")
        }
      }
    }
  }

  @Test
  fun `test DependencyAssertions#assertModuleDependency`() {
    runBlocking {
      project.workspaceModel.update {
        addLibraryEntity("library1")
        addLibraryEntity("library2")
        addModuleEntity("project") {
          addModuleDependency("project.module1")
          addModuleDependency("project.module2")
          addLibraryDependency("library1")
          addLibraryDependency("library2")
        }
        addModuleEntity("project.module1") {
          addModuleDependency("project.module2")
          addLibraryDependency("library2")
        }
        addModuleEntity("project.module2")
      }
      ModuleAssertions.assertModuleEntity(project, "project") { module ->
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertModuleDependency(module, "library1")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertModuleDependency(module, "library2")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertModuleDependency(module, "project")
        }

        DependencyAssertions.assertModuleDependency(module, "project.module1")

        DependencyAssertions.assertModuleDependency(module, "project.module2")
      }
      ModuleAssertions.assertModuleEntity(project, "project.module1") { module ->
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertModuleDependency(module, "library1")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertModuleDependency(module, "library2")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertModuleDependency(module, "project")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertModuleDependency(module, "project.module1")
        }

        DependencyAssertions.assertModuleDependency(module, "project.module2")
      }
      ModuleAssertions.assertModuleEntity(project, "project.module2") { module ->
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertModuleDependency(module, "library1")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertModuleDependency(module, "library2")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertModuleDependency(module, "project")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertModuleDependency(module, "project.module1")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertModuleDependency(module, "project.module2")
        }
      }
    }
  }

  @Test
  fun `test DependencyAssertions#assertLibraryDependency`() {
    runBlocking {
      project.workspaceModel.update {
        addLibraryEntity("library1")
        addLibraryEntity("library2")
        addModuleEntity("project") {
          addModuleDependency("project.module1")
          addModuleDependency("project.module2")
          addLibraryDependency("library1")
          addLibraryDependency("library2")
        }
        addModuleEntity("project.module1") {
          addModuleDependency("project.module2")
          addLibraryDependency("library2")
        }
        addModuleEntity("project.module2")
      }
      ModuleAssertions.assertModuleEntity(project, "project") { module ->

        DependencyAssertions.assertLibraryDependency(module, "library1")

        DependencyAssertions.assertLibraryDependency(module, "library2")

        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependency(module, "project")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependency(module, "project.module1")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependency(module, "project.module2")
        }
      }
      ModuleAssertions.assertModuleEntity(project, "project.module1") { module ->
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependency(module, "library1")
        }

        DependencyAssertions.assertLibraryDependency(module, "library2")

        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependency(module, "project")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependency(module, "project.module1")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependency(module, "project.module2")
        }
      }
      ModuleAssertions.assertModuleEntity(project, "project.module2") { module ->
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependency(module, "library1")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependency(module, "library2")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependency(module, "project")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependency(module, "project.module1")
        }
        Assertions.assertThrows(AssertionError::class.java) {
          DependencyAssertions.assertLibraryDependency(module, "project.module2")
        }
      }
    }
  }
}