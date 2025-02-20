// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.assertion.moduleAssertion

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertContainsUnordered
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsUnordered
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.entities
import org.junit.jupiter.api.Assertions

object ModuleAssertions {

  @JvmStatic
  fun assertModules(project: Project, vararg expectedNames: String) {
    assertModules(project, expectedNames.asList())
  }

  @JvmStatic
  fun assertModules(project: Project, expectedNames: List<String>) {
    val storage = project.workspaceModel.currentSnapshot
    assertModules(storage, expectedNames)
  }

  @JvmStatic
  fun assertModules(storage: EntityStorage, vararg expectedNames: String) {
    assertModules(storage, expectedNames.asList())
  }

  @JvmStatic
  fun assertModules(storage: EntityStorage, expectedNames: List<String>) {
    val actualNames = storage.entities<ModuleEntity>().map { it.name }.toList()
    assertEqualsUnordered(expectedNames, actualNames)
  }

  @JvmStatic
  fun assertModulesContains(project: Project, vararg expectedNames: String) {
    assertModulesContains(project, expectedNames.asList())
  }

  @JvmStatic
  fun assertModulesContains(project: Project, expectedNames: List<String>) {
    val storage = project.workspaceModel.currentSnapshot
    assertModulesContains(storage, expectedNames)
  }

  @JvmStatic
  fun assertModulesContains(storage: EntityStorage, vararg expectedNames: String) {
    assertModulesContains(storage, expectedNames.asList())
  }

  @JvmStatic
  fun assertModulesContains(storage: EntityStorage, expectedNames: List<String>) {
    val actualNames = storage.entities<ModuleEntity>().map { it.name }.toList()
    assertContainsUnordered(expectedNames, actualNames)
  }

  fun assertModuleEntity(project: Project, name: String, assertion: (ModuleEntity) -> Unit) =
    assertModuleEntity(project.workspaceModel.currentSnapshot, name, assertion)

  fun assertModuleEntity(storage: EntityStorage, name: String, assertion: (ModuleEntity) -> Unit) {
    storage.entities<ModuleEntity>()
      .find { it.name == name }
      .let { module ->
        Assertions.assertNotNull(module, "Cannot find '$name' module")
        Assertions.assertEquals(name, module!!.name)
        assertion(module)
      }
  }
}