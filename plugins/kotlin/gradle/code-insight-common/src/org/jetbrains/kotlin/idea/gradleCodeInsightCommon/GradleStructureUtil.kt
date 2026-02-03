// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleGrouper
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootGroup
import org.jetbrains.kotlin.idea.base.projectStructure.toModuleGroup

/**
 * A class representing a project's Gradle module structure.
 */
internal class GradleModuleTreeNode(
    val module: ModuleSourceRootGroup,
    val children: List<GradleModuleTreeNode>
)

internal fun Project.buildGradleModuleHierarchy(rootModule: Module): GradleModuleTreeNode? {
    val moduleRoots = modules.map {
        it.toModuleGroup()
    }.distinctBy { it.baseModule }

    val rootModuleRoot = moduleRoots.firstOrNull { it.baseModule == rootModule } ?: return null

    val grouper = ModuleGrouper.instanceFor(this)

    val moduleRootGroups = moduleRoots.groupBy { grouper.getGroupPath(it.baseModule) }
    val currentPath = mutableListOf<String>()

    fun buildHierarchyRecursively(currentNode: ModuleSourceRootGroup): GradleModuleTreeNode {
        val shortenedName = grouper.getShortenedNameByFullModuleName(currentNode.baseModule.name)

        currentPath.add(shortenedName)
        val children = moduleRootGroups[currentPath]?.map {
            buildHierarchyRecursively(it)
        } ?: emptyList()
        currentPath.removeLast()

        return GradleModuleTreeNode(currentNode, children)
    }

    return buildHierarchyRecursively(rootModuleRoot)
}
