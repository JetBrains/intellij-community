// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.DependencyScope
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.ModuleDependency
import com.intellij.platform.workspace.jps.entities.ModuleDependencyItem
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.workspaceModel.ide.NonPersistentEntitySource

class KotlinExportedDependenciesCollectorTest : HeavyPlatformTestCase() {
    /**
     * The aggregator depends on all other modules, and each of these modules depends back on the aggregator. All dependencies are exported,
     * so the exported closure of each module is the whole project.
     *
     * The main focus of the test is correctness, so it has no time limit. But the project is large, so the test is also very slow if the
     * computation of the exported dependencies is cubic in the number of modules (KT-88724).
     */
    fun testAggregatorCycle() {
        val moduleIds = (0 until MODULE_COUNT).map { ModuleId("module$it") }
        val aggregatorId = ModuleId("aggregator")
        val libraryId = LibraryId("library", LibraryTableId.ProjectLibraryTableId)

        runWriteActionAndWait {
            project.workspaceModel.updateProjectModel("Create the aggregator cycle") { storage ->
                storage addEntity ModuleEntity(
                    aggregatorId.name,
                    moduleIds.map { ModuleDependency(it, exported = true, DependencyScope.COMPILE, productionOnTest = false) },
                    NonPersistentEntitySource,
                )

                moduleIds.forEachIndexed { index, moduleId ->
                    // The first module depends on the aggregator with a different scope to check which occurrence of the aggregator wins.
                    val aggregatorScope = if (index == 0) DependencyScope.TEST else DependencyScope.COMPILE
                    val dependencies = buildList {
                        add(ModuleDependency(aggregatorId, exported = true, aggregatorScope, productionOnTest = false))
                        if (index == MODULE_COUNT / 2) {
                            add(LibraryDependency(libraryId, exported = true, DependencyScope.COMPILE))
                        }
                    }
                    storage addEntity ModuleEntity(moduleId.name, dependencies, NonPersistentEntitySource)
                }
            }
        }

        val aggregatorEntity = aggregatorId.resolve(project.workspaceModel.currentSnapshot) ?: error("Cannot find the aggregator entity.")
        val collector = KotlinExportedDependenciesCollector.getInstance(project)
        val expectedKeys = buildSet {
            addAll(moduleIds)
            add(aggregatorId)
            add(libraryId)
        }

        aggregatorEntity.dependencies.forEachIndexed { index, dependency ->
            val exportedDependencies = collector.getExportedDependencies(dependency as ModuleDependency)
            val keys = exportedDependencies.map { it.key }

            assertEquals("Exported dependencies of '${dependency.module.name}' contain duplicates.", keys.size, keys.toSet().size)
            assertEquals(expectedKeys, keys.toSet())

            val aggregatorDependency = exportedDependencies.first { it.key == aggregatorId } as ModuleDependency
            val expectedAggregatorScope = if (index == 0) DependencyScope.TEST else DependencyScope.COMPILE
            assertEquals(expectedAggregatorScope, aggregatorDependency.scope)
        }
    }

    private val ModuleDependencyItem.key: Any
        get() = when (this) {
            is LibraryDependency -> library
            is ModuleDependency -> module
            else -> error("Unexpected dependency type: $this")
        }

    private companion object {
        const val MODULE_COUNT = 2000
    }
}
