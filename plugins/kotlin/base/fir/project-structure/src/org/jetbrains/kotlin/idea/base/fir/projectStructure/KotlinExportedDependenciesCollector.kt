// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import org.jetbrains.kotlin.analysis.api.platform.analysisMessageBus
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinGlobalModuleStateModificationListener
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinGlobalSourceModuleStateModificationListener
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationTopics
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModuleStateModificationListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * A service which computes exported dependencies for a [ModuleDependencyItem].
 * The result of the computation is a list of only exported dependencies from the given dependency.
 */
@Service(Service.Level.PROJECT)
internal class KotlinExportedDependenciesCollector(private val project: Project) : Disposable {
    private val cache: ConcurrentMap<ModuleDependency, List<ModuleDependencyItem>> = ConcurrentHashMap()

    init {
        project.analysisMessageBus.connect(this).apply {
            subscribe(
                KotlinModificationTopics.MODULE_STATE_MODIFICATION,
                KotlinModuleStateModificationListener { _, _ -> dropCaches() },
            )

            subscribe(
                KotlinModificationTopics.GLOBAL_MODULE_STATE_MODIFICATION,
                KotlinGlobalModuleStateModificationListener { dropCaches() },
            )

            subscribe(
                KotlinModificationTopics.GLOBAL_SOURCE_MODULE_STATE_MODIFICATION,
                KotlinGlobalSourceModuleStateModificationListener { dropCaches() },
            )
        }
    }

    private fun dropCaches() {
        cache.clear()
    }

    fun getExportedDependencies(dependency: ModuleDependency): List<ModuleDependencyItem> {
        cache[dependency]?.let { return it }
        val currentSnapshot = project.workspaceModel.currentSnapshot
        return computeDependencies(dependency, currentSnapshot)
    }

    private fun computeDependencies(
        computeDependenciesOf: ModuleDependency,
        currentSnapshot: ImmutableEntityStorage,
    ): List<ModuleDependencyItem> {
        val visitedModules = linkedSetOf<ModuleId>()
        val rawResult = mutableListOf<ModuleDependencyItem>()

        fun visit(currentDependency: ModuleDependency) {
            if (currentDependency.module in visitedModules) return
            visitedModules += currentDependency.module

            cache[currentDependency]?.let {
                rawResult += it
                return
            }

            val moduleEntity = currentDependency.module.resolve(currentSnapshot) ?: return

            for (transitiveDependency in moduleEntity.dependencies) {
                when (transitiveDependency) {
                    is LibraryDependency -> {
                        if (transitiveDependency.exported) {
                            rawResult += transitiveDependency
                        }
                    }

                    is ModuleDependency ->
                        if (transitiveDependency.exported) {
                            rawResult += transitiveDependency
                            visit(transitiveDependency)
                        }

                    is SdkDependency -> {}
                    ModuleSourceDependency -> {}
                    InheritedSdkDependency -> {}
                }
            }
        }

        visit(computeDependenciesOf)

        val result = rawResult
            .distinctBy { dep ->
                when (dep) {
                    is LibraryDependency -> dep.library
                    is ModuleDependency -> dep.module
                    else -> error("Unexpected dependency type: $computeDependenciesOf")
                }
            }

        return cache.putIfAbsent(computeDependenciesOf, result) ?: result
    }

    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): KotlinExportedDependenciesCollector =
            project.getService(KotlinExportedDependenciesCollector::class.java)
    }
}