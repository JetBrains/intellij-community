// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.base.facet.implementedModules
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.facet.kotlinSourceRootType
import org.jetbrains.kotlin.idea.base.projectStructure.kotlinSourceRootType
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.projectStructure.unwrapModuleSourceInfo
import org.jetbrains.kotlin.idea.caches.project.*
import org.jetbrains.kotlin.resolve.ModulePath
import org.jetbrains.kotlin.resolve.ModuleStructureOracle
import java.util.*

class IdeaModuleStructureOracle : ModuleStructureOracle {
    override fun hasImplementingModules(module: ModuleDescriptor): Boolean {
        return module.implementingDescriptors.isNotEmpty()
    }

    override fun findAllReversedDependsOnPaths(module: ModuleDescriptor): List<ModulePath> {
        val currentPath: Stack<ModuleInfo> = Stack()

        return sequence<ModuleInfoPath> {
            val root = module.moduleInfo
            if (root != null) {
                yieldPathsFromSubgraph(
                    root,
                    currentPath,
                    getChildren = {
                        with(DependsOnGraphHelper) { it.unwrapModuleSourceInfo()?.predecessorsInDependsOnGraph() ?: emptyList() }
                    }
                )
            }
        }.map {
            ProgressManager.checkCanceled()
            it.toModulePath()
        }.toList()
    }

    override fun findAllDependsOnPaths(module: ModuleDescriptor): List<ModulePath> {
        val currentPath: Stack<ModuleInfo> = Stack()

        return sequence<ModuleInfoPath> {
            val root = module.moduleInfo
            if (root != null) {
                yieldPathsFromSubgraph(
                    root,
                    currentPath,
                    getChildren = {
                        with(DependsOnGraphHelper) { it.unwrapModuleSourceInfo()?.successorsInDependsOnGraph() ?: emptyList() }
                    }
                )
            }
        }.map {
            ProgressManager.checkCanceled()
            it.toModulePath()
        }.toList()
    }

    private suspend fun SequenceScope<ModuleInfoPath>.yieldPathsFromSubgraph(
        root: ModuleInfo,
        currentPath: Stack<ModuleInfo>,
        getChildren: (ModuleInfo) -> List<ModuleInfo>
    ) {
        ProgressManager.checkCanceled()

        currentPath.push(root)

        val children = getChildren(root)
        if (children.isEmpty()) {
            yield(ModuleInfoPath(currentPath.toList()))
        } else {
            children.forEach {
                yieldPathsFromSubgraph(it, currentPath, getChildren)
            }
        }

        currentPath.pop()
    }

    private class ModuleInfoPath(val nodes: List<ModuleInfo>)

    private fun ModuleInfoPath.toModulePath(): ModulePath =
        ModulePath(nodes.mapNotNull {
            ProgressManager.checkCanceled()
            it.unwrapModuleSourceInfo()?.toDescriptor()
        })
}

object DependsOnGraphHelper {
    fun ModuleDescriptor.predecessorsInDependsOnGraph(): List<ModuleDescriptor> {
        return moduleSourceInfo
            ?.predecessorsInDependsOnGraph()
            ?.mapNotNull {
                ProgressManager.checkCanceled()
                it.toDescriptor()
            }
            ?: emptyList()
    }

    fun ModuleSourceInfo.predecessorsInDependsOnGraph(): List<ModuleSourceInfo> {
        val sourceRootType = this.kotlinSourceRootType ?: return emptyList()
        return this.module.predecessorsInDependsOnGraph().mapNotNull { it.getModuleInfo(sourceRootType) }
    }

    private fun Module.predecessorsInDependsOnGraph(): List<Module> {
        return implementingModules
    }

    fun ModuleDescriptor.successorsInDependsOnGraph(): List<ModuleDescriptor> {
        return moduleSourceInfo
            ?.successorsInDependsOnGraph()
            ?.mapNotNull {
                ProgressManager.checkCanceled()
                it.toDescriptor()
            }
            ?: emptyList()
    }

    fun ModuleSourceInfo.successorsInDependsOnGraph(): List<ModuleSourceInfo> {
        return module.successorsInDependsOnGraph().mapNotNull { module ->
            ProgressManager.checkCanceled()

            val sourceRootType = module.kotlinSourceRootType ?: return@mapNotNull null
            module.getModuleInfo(sourceRootType)
        }
    }

    private fun Module.successorsInDependsOnGraph(): List<Module> {
        return implementedModules
    }
}

private val ModuleDescriptor.moduleInfo: ModuleInfo?
    get() = getCapability(ModuleInfo.Capability)?.unwrapModuleSourceInfo()

private val ModuleDescriptor.moduleSourceInfo: ModuleSourceInfo?
    get() = moduleInfo as? ModuleSourceInfo