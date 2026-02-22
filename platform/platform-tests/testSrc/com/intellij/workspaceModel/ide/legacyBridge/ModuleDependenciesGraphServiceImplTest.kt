// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.projectModel.ModuleDependenciesGraphService
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@TestApplication
class ModuleDependenciesGraphServiceImplTest {
  @JvmField
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  private val graph: ModuleDependenciesGraphService
    get() = ModuleDependenciesGraphService.getInstance(projectModel.project)

  @Test
  fun `single module with no dependencies`() {
    val moduleA = projectModel.createModule("module-a").findModuleEntity()!!

    val exportedGraph = graph.getModuleDependenciesGraph()

    assertThat(exportedGraph.getModuleDependants(moduleA).toList())
      .describedAs("Module with no dependencies should have no incoming edges")
      .isEmpty()
  }

  @Test
  fun `linear chain with all exported dependencies`() {
    // A ← B (exported) ← C (exported)
    val moduleA = projectModel.createModule("module-a")
    val moduleB = projectModel.createModule("module-b")
    val moduleC = projectModel.createModule("module-c")

    ModuleRootModificationUtil.addDependency(moduleB, moduleA, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(moduleC, moduleB, DependencyScope.COMPILE, true)

    val exportedGraph = graph.getModuleDependenciesGraph()
    val moduleAEntity = moduleA.findModuleEntity()!!
    val moduleBEntity = moduleB.findModuleEntity()!!

    assertThat(exportedGraph.getModuleDependants(moduleAEntity).map { it.name }.toList())
      .describedAs("Module A should have B and C as transitive dependents (both exported)")
      .containsExactlyInAnyOrder("module-b", "module-c")

    assertThat(exportedGraph.getModuleDependants(moduleBEntity).map { it.name }.toList())
      .describedAs("Module B should have C as dependent")
      .containsExactlyInAnyOrder("module-c")
  }

  @Test
  fun `non-exported dependency breaks transitive chain`() {
    // A ← B (NOT exported) ← C (exported)
    val moduleA = projectModel.createModule("module-a")
    val moduleB = projectModel.createModule("module-b")
    val moduleC = projectModel.createModule("module-c")

    ModuleRootModificationUtil.addDependency(moduleB, moduleA, DependencyScope.COMPILE, false)
    ModuleRootModificationUtil.addDependency(moduleC, moduleB, DependencyScope.COMPILE, true)

    val exportedGraph = graph.getModuleDependenciesGraph()
    val moduleAEntity = moduleA.findModuleEntity()!!
    val moduleBEntity = moduleB.findModuleEntity()!!

    assertThat(exportedGraph.getModuleDependants(moduleAEntity).map { it.name }.toList())
      .describedAs("Module A should only have B (C can't reach A transitively because B doesn't export A)")
      .containsExactlyInAnyOrder("module-b")

    assertThat(exportedGraph.getModuleDependants(moduleBEntity).map { it.name }.toList())
      .describedAs("Module B should have C as dependent")
      .containsExactlyInAnyOrder("module-c")
  }

  @Test
  fun `diamond dependency with all exported`() {
    // A ← B (exported) ← D (exported)
    // A ← C (exported) ← D (exported)
    val moduleA = projectModel.createModule("module-a")
    val moduleB = projectModel.createModule("module-b")
    val moduleC = projectModel.createModule("module-c")
    val moduleD = projectModel.createModule("module-d")

    ModuleRootModificationUtil.addDependency(moduleB, moduleA, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(moduleC, moduleA, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(moduleD, moduleB, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(moduleD, moduleC, DependencyScope.COMPILE, true)

    val exportedGraph = graph.getModuleDependenciesGraph()
    val moduleAEntity = moduleA.findModuleEntity()!!

    assertThat(exportedGraph.getModuleDependants(moduleAEntity).map { it.name }.toList())
      .describedAs("Module A should have B, C, and D as dependents (all paths exported)")
      .containsExactlyInAnyOrder("module-b", "module-c", "module-d")
  }

  @Test
  fun `diamond dependency with mixed export flags`() {
    // A ← B (exported) ← D (exported)
    // A ← C (NOT exported) ← D (exported)
    val moduleA = projectModel.createModule("module-a")
    val moduleB = projectModel.createModule("module-b")
    val moduleC = projectModel.createModule("module-c")
    val moduleD = projectModel.createModule("module-d")

    ModuleRootModificationUtil.addDependency(moduleB, moduleA, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(moduleC, moduleA, DependencyScope.COMPILE, false)
    ModuleRootModificationUtil.addDependency(moduleD, moduleB, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(moduleD, moduleC, DependencyScope.COMPILE, true)

    val exportedGraph = graph.getModuleDependenciesGraph()
    val moduleAEntity = moduleA.findModuleEntity()!!
    val moduleCEntity = moduleC.findModuleEntity()!!

    assertThat(exportedGraph.getModuleDependants(moduleAEntity).map { it.name }.toList())
      .describedAs("Module A should have B, C, and D (D reaches A via B's exported path)")
      .containsExactlyInAnyOrder("module-b", "module-c", "module-d")

    assertThat(exportedGraph.getModuleDependants(moduleCEntity).map { it.name }.toList())
      .describedAs("Module C should only have D (no transitive propagation because C doesn't export A)")
      .containsExactlyInAnyOrder("module-d")
  }

  @Test
  fun `multiple independent dependency chains`() {
    // Chain 1: A ← B (exported)
    // Chain 2: C ← D (exported)
    val moduleA = projectModel.createModule("module-a")
    val moduleB = projectModel.createModule("module-b")
    val moduleC = projectModel.createModule("module-c")
    val moduleD = projectModel.createModule("module-d")

    ModuleRootModificationUtil.addDependency(moduleB, moduleA, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(moduleD, moduleC, DependencyScope.COMPILE, true)

    val exportedGraph = graph.getModuleDependenciesGraph()
    val moduleAEntity = moduleA.findModuleEntity()!!
    val moduleCEntity = moduleC.findModuleEntity()!!

    assertThat(exportedGraph.getModuleDependants(moduleAEntity).map { it.name }.toList())
      .describedAs("Module A should only have B from its chain")
      .containsExactlyInAnyOrder("module-b")

    assertThat(exportedGraph.getModuleDependants(moduleCEntity).map { it.name }.toList())
      .describedAs("Module C should only have D from its chain")
      .containsExactlyInAnyOrder("module-d")
  }

  @Test
  fun `complex graph with multiple paths`() {
    // A ← B (exported) ← D (exported)
    // A ← C (exported) ← D (exported)
    // B ← E (exported)
    // C ← E (exported)
    val moduleA = projectModel.createModule("module-a")
    val moduleB = projectModel.createModule("module-b")
    val moduleC = projectModel.createModule("module-c")
    val moduleD = projectModel.createModule("module-d")
    val moduleE = projectModel.createModule("module-e")

    ModuleRootModificationUtil.addDependency(moduleB, moduleA, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(moduleC, moduleA, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(moduleD, moduleB, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(moduleD, moduleC, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(moduleE, moduleB, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(moduleE, moduleC, DependencyScope.COMPILE, true)

    val exportedGraph = graph.getModuleDependenciesGraph()
    val moduleAEntity = moduleA.findModuleEntity()!!

    assertThat(exportedGraph.getModuleDependants(moduleAEntity).map { it.name }.toList())
      .describedAs("Module A should have B, C, D, and E (E reaches A via both B and C)")
      .containsExactlyInAnyOrder("module-b", "module-c", "module-d", "module-e")
  }

  @Test
  fun `different dependency scopes with exported flag`() {
    // A ← B (exported, TEST scope) ← C (exported, RUNTIME scope)
    val moduleA = projectModel.createModule("module-a")
    val moduleB = projectModel.createModule("module-b")
    val moduleC = projectModel.createModule("module-c")

    ModuleRootModificationUtil.addDependency(moduleB, moduleA, DependencyScope.TEST, true)
    ModuleRootModificationUtil.addDependency(moduleC, moduleB, DependencyScope.RUNTIME, true)

    val exportedGraph = graph.getModuleDependenciesGraph()
    val moduleAEntity = moduleA.findModuleEntity()!!

    assertThat(exportedGraph.getModuleDependants(moduleAEntity).map { it.name }.toList())
      .describedAs("Exported flag should work regardless of dependency scope")
      .containsExactlyInAnyOrder("module-b", "module-c")
  }

  @Test
  fun `circular dependency with all exported`() {
    // A ← B (exported) ← C (exported) ← A (exported)
    val moduleA = projectModel.createModule("module-a")
    val moduleB = projectModel.createModule("module-b")
    val moduleC = projectModel.createModule("module-c")

    ModuleRootModificationUtil.addDependency(moduleB, moduleA, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(moduleC, moduleB, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(moduleA, moduleC, DependencyScope.COMPILE, true)

    val exportedGraph = graph.getModuleDependenciesGraph()
    val moduleAEntity = moduleA.findModuleEntity()!!
    val moduleBEntity = moduleB.findModuleEntity()!!
    val moduleCEntity = moduleC.findModuleEntity()!!

    // In a circular dependency with all exported, each module sees all modules in the cycle (including itself)
    assertThat(exportedGraph.getModuleDependants(moduleAEntity).map { it.name }.toList())
      .describedAs("Module A should have B, C, and itself as dependents in circular chain")
      .containsExactlyInAnyOrder("module-a", "module-b", "module-c")

    assertThat(exportedGraph.getModuleDependants(moduleBEntity).map { it.name }.toList())
      .describedAs("Module B should have C, A, and itself as dependents in circular chain")
      .containsExactlyInAnyOrder("module-a", "module-b", "module-c")

    assertThat(exportedGraph.getModuleDependants(moduleCEntity).map { it.name }.toList())
      .describedAs("Module C should have A, B, and itself as dependents in circular chain")
      .containsExactlyInAnyOrder("module-a", "module-b", "module-c")
  }

  @Test
  fun `circular dependency with non-exported break`() {
    // A ← B (exported) ← C (NOT exported) ← A (exported)
    val moduleA = projectModel.createModule("module-a")
    val moduleB = projectModel.createModule("module-b")
    val moduleC = projectModel.createModule("module-c")

    ModuleRootModificationUtil.addDependency(moduleB, moduleA, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(moduleC, moduleB, DependencyScope.COMPILE, false)
    ModuleRootModificationUtil.addDependency(moduleA, moduleC, DependencyScope.COMPILE, true)

    val exportedGraph = graph.getModuleDependenciesGraph()
    val moduleBEntity = moduleB.findModuleEntity()!!

    assertThat(exportedGraph.getModuleDependants(moduleBEntity).map { it.name }.toList())
      .describedAs("Non-exported dependency breaks circular chain; C shouldn't propagate to A")
      .containsExactlyInAnyOrder("module-c")
  }

  @Test
  fun `transitive exported dependencies validation`() {
    // A ← B (exported) ← C (exported) ← D (NOT exported)
    // D is added to A's dependents because it directly depends on C
    // But D is not added to the BFS queue because D doesn't export C
    val moduleA = projectModel.createModule("module-a")
    val moduleB = projectModel.createModule("module-b")
    val moduleC = projectModel.createModule("module-c")
    val moduleD = projectModel.createModule("module-d")

    ModuleRootModificationUtil.addDependency(moduleB, moduleA, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(moduleC, moduleB, DependencyScope.COMPILE, true)
    ModuleRootModificationUtil.addDependency(moduleD, moduleC, DependencyScope.COMPILE, false)

    val exportedGraph = graph.getModuleDependenciesGraph()
    val moduleAEntity = moduleA.findModuleEntity()!!
    val moduleCEntity = moduleC.findModuleEntity()!!
    val moduleBEntity = moduleB.findModuleEntity()!!

    // A's dependents: B (direct, exported), C (transitive via B), D (transitive via C, but D doesn't export C)
    assertThat(exportedGraph.getModuleDependants(moduleAEntity).map { it.name }.toList())
      .describedAs("Module A should have B, C, and D as dependents (D is included because C is in the queue)")
      .containsExactlyInAnyOrder("module-b", "module-c", "module-d")

    // B's dependents: C (direct, exported), D (transitive via C)
    assertThat(exportedGraph.getModuleDependants(moduleBEntity).map { it.name }.toList())
      .describedAs("Module B should have C and D as dependents")
      .containsExactlyInAnyOrder("module-c", "module-d")

    // C's dependents: only D (direct, not exported)
    assertThat(exportedGraph.getModuleDependants(moduleCEntity).map { it.name }.toList())
      .describedAs("Module C should only have D (no transitive propagation because D doesn't export C)")
      .containsExactlyInAnyOrder("module-d")
  }

  @Test
  fun `getIn returns direct dependents`() {
    // A ← B (NOT exported)
    // A ← C (NOT exported)
    val moduleA = projectModel.createModule("module-a")
    val moduleB = projectModel.createModule("module-b")
    val moduleC = projectModel.createModule("module-c")

    ModuleRootModificationUtil.addDependency(moduleB, moduleA, DependencyScope.COMPILE, false)
    ModuleRootModificationUtil.addDependency(moduleC, moduleA, DependencyScope.COMPILE, false)

    val exportedGraph = graph.getModuleDependenciesGraph()
    val moduleAEntity = moduleA.findModuleEntity()!!

    assertThat(exportedGraph.getModuleDependants(moduleAEntity).map { it.name }.toList())
      .describedAs("getIn() should return direct dependents even when not exported")
      .containsExactlyInAnyOrder("module-b", "module-c")
  }
}
