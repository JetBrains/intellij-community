// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dependencyAnalyzer

import com.intellij.openapi.externalSystem.model.project.dependencies.DependencyScopeNode
import com.intellij.openapi.project.modules
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions
import org.jetbrains.plugins.gradle.service.project.GradleModuleDataIndex
import org.jetbrains.plugins.gradle.testFramework.GradleExecutionTestCase
import org.jetbrains.plugins.gradle.util.GradleModuleData
import org.junit.jupiter.api.Assertions

abstract class GradleDependencyNodeIndexTestCase : GradleExecutionTestCase() {

  fun getGradleModuleData(moduleName: String): GradleModuleData {
    val module = project.modules.firstOrNull { it.name == moduleName }
    Assertions.assertNotNull(module, "Cannot find 'moduleName' module")
    val moduleData = GradleModuleDataIndex.findGradleModuleData(module!!)
    Assertions.assertNotNull(moduleData, "Cannot find module data for 'moduleName' module")
    return moduleData!!
  }

  fun assertNonEmptyDependencyScopeNodes(
    expectedDependencyNodes: List<Pair<String, String>>,
    actualDependencyNodes: List<DependencyScopeNode>
  ) {
    CollectionAssertions.assertEqualsUnordered(
      expectedDependencyNodes,
      actualDependencyNodes.flatMap { scope ->
        scope.dependencies.map {
          scope.scope to it.displayName
        }
      }
    )
  }
}