// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.assertion.dependencyAssertion

import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsUnordered
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.entities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

object DependencyAssertions {
  @JvmStatic
  fun assertModuleLibDep(storage: EntityStorage, moduleName: String, depName: String) {
    val modules = storage.entities<ModuleEntity>().filter { it.name == moduleName }.toList()
    assertEquals(1, modules.size)
    assertTrue(modules[0].dependencies.filter { it is LibraryDependency }.map { (it as LibraryDependency).library.name }.contains(depName))
  }

  @JvmStatic
  fun assertModuleModuleDeps(storage: EntityStorage, moduleName: String, vararg depNames: String) {
    val modules = storage.entities<ModuleEntity>().filter { it.name == moduleName }.toList()
    assertEquals(1, modules.size)
    val moduleDeps = modules[0].dependencies.filterIsInstance<ModuleDependency>().map { it.module.name }.toList()
    assertEqualsUnordered(depNames.toList(), moduleDeps)
  }
}