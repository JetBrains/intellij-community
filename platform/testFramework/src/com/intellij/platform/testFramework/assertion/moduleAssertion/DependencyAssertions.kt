// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.assertion.moduleAssertion

import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsUnordered
import com.intellij.platform.workspace.jps.entities.*
import org.junit.jupiter.api.Assertions

object DependencyAssertions {

  val INHERITED_SDK = InheritedSdkDependency::class.simpleName!!
  val MODULE_SOURCE = ModuleSourceDependency::class.simpleName!!

  fun assertDependencies(module: ModuleEntity, vararg expectedNames: String) {
    assertDependencies(module, expectedNames.asList())
  }

  fun assertDependencies(module: ModuleEntity, expectedNames: List<String>) {
    val actualNames = module.dependencies.map { dependency ->
      when (dependency) {
        InheritedSdkDependency -> INHERITED_SDK
        ModuleSourceDependency -> MODULE_SOURCE
        is LibraryDependency -> dependency.library.name
        is ModuleDependency -> dependency.module.name
        is SdkDependency -> dependency.sdk.name
      }
    }
    assertEqualsUnordered(expectedNames, actualNames)
  }

  fun assertLibraryDependency(module: ModuleEntity, name: String, assertion: (LibraryDependency) -> Unit) {
    module.dependencies.filterIsInstance<LibraryDependency>()
      .find { it.library.name == name }
      .let { dependency ->
        Assertions.assertNotNull(dependency, "Cannot find '$name' library dependency in '${module.name}' module")
        Assertions.assertEquals(name, dependency!!.library.name)
        assertion(dependency)
      }
  }

  fun assertModuleDependency(module: ModuleEntity, name: String, assertion: (ModuleDependency) -> Unit) {
    module.dependencies.filterIsInstance<ModuleDependency>()
      .find { it.module.name == name }
      .let { dependency ->
        Assertions.assertNotNull(dependency, "Cannot find '$name' module dependency in '${module.name}' module")
        Assertions.assertEquals(name, dependency!!.module.name)
        assertion(dependency)
      }
  }
}