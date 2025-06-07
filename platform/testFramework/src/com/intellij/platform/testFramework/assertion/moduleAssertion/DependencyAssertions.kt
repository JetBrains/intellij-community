// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.assertion.moduleAssertion

import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions
import com.intellij.platform.workspace.jps.entities.*
import org.junit.jupiter.api.Assertions

object DependencyAssertions {

  val INHERITED_SDK: String = InheritedSdkDependency::class.simpleName!!
  val MODULE_SOURCE: String = ModuleSourceDependency::class.simpleName!!

  private val ModuleDependencyItem.dependencyName: String
    get() = when (this) {
      is ModuleSourceDependency -> MODULE_SOURCE
      is InheritedSdkDependency -> INHERITED_SDK
      is SdkDependency -> sdk.name
      is LibraryDependency -> library.name
      is ModuleDependency -> module.name
    }

  @JvmStatic
  fun assertDependencies(module: ModuleEntity, vararg expectedNames: String) {
    assertDependencies(module, expectedNames.asList())
  }

  @JvmStatic
  fun assertLibraryDependencies(module: ModuleEntity, vararg expectedNames: String) {
    assertLibraryDependencies(module, expectedNames.asList())
  }

  @JvmStatic
  fun assertLibraryDependencies(module: ModuleEntity, expectedNames: List<String>) {
    assertDependencies(module, LibraryDependency::class.java, expectedNames)
  }

  @JvmStatic
  fun assertModuleDependencies(module: ModuleEntity, vararg expectedNames: String) {
    assertModuleDependencies(module, expectedNames.asList())
  }

  @JvmStatic
  fun assertModuleDependencies(module: ModuleEntity, expectedNames: List<String>) {
    assertDependencies(module, ModuleDependency::class.java, expectedNames)
  }

  @JvmStatic
  fun assertLibraryDependency(module: ModuleEntity, name: String, assertion: (LibraryDependency) -> Unit = {}) {
    assertDependency(module, LibraryDependency::class.java, name, assertion)
  }

  @JvmStatic
  fun assertModuleDependency(module: ModuleEntity, name: String, assertion: (ModuleDependency) -> Unit = {}) {
    assertDependency(module, ModuleDependency::class.java, name, assertion)
  }

  @JvmStatic
  fun assertDependencies(module: ModuleEntity, expectedNames: List<String>) {
    module.dependencies
      .map { it.dependencyName }
      .let { actualNames ->
        CollectionAssertions.assertEqualsUnordered(expectedNames, actualNames)
      }
  }

  private fun <T : ModuleDependencyItem> assertDependencies(module: ModuleEntity, type: Class<T>, expectedNames: List<String>) {
    module.dependencies.filterIsInstance(type)
      .map { it.dependencyName }
      .let { actualNames ->
        CollectionAssertions.assertEqualsUnordered(expectedNames, actualNames)
      }
  }

  private fun <T : ModuleDependencyItem> assertDependency(module: ModuleEntity, type: Class<T>, name: String, assertion: (T) -> Unit = {}) {
    module.dependencies.filterIsInstance(type)
      .find { it.dependencyName == name }
      .let { dependency ->
        Assertions.assertNotNull(dependency, "Cannot find '$name' (${type.simpleName}) dependency in '${module.name}' module")
        assertion(dependency!!)
      }
  }
}