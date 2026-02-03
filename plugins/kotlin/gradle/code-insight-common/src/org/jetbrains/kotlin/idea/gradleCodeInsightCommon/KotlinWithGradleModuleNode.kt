// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootGroup
import org.jetbrains.kotlin.idea.base.projectStructure.toModuleGroup
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.getGradleKotlinVersion
import org.jetbrains.kotlin.idea.configuration.getRootModule
import org.jetbrains.kotlin.idea.configuration.hasKotlinPluginEnabled

/**
 * Represents a node in the Gradle module structure along with the associated defined Kotlin versions.
 * This class can be used to determine which version might have to be used in a submodule or if there is a version conflict.
 */
internal class KotlinWithGradleModuleNode(
    private val module: ModuleSourceRootGroup,
    internal val parent: KotlinWithGradleModuleNode? = null,
    internal val definedKotlinVersion: IdeKotlinVersion? = null
) {

    private val _children: MutableList<KotlinWithGradleModuleNode> = mutableListOf()

    val children: List<KotlinWithGradleModuleNode>
        get() = _children

    // All versions defined anywhere as a submodule below this module, even transitively
    private fun definedSubmoduleKotlinVersions(kotlinVersions: MutableSet<IdeKotlinVersion> = mutableSetOf()): Set<IdeKotlinVersion> {
        definedKotlinVersion?.let {
            kotlinVersions.add(it)
        }
        children.forEach { it.definedSubmoduleKotlinVersions(kotlinVersions) }
        return kotlinVersions
    }

    private data class KotlinVersionResult(val conflict: Boolean, val forcedVersion: IdeKotlinVersion?)

    private val possibleKotlinVersion: KotlinVersionResult by lazy {
        val inheritedVersion = definedKotlinVersion ?: parent?.getForcedKotlinVersion()
        val childVersions = definedSubmoduleKotlinVersions()

        if (childVersions.size > 1) return@lazy KotlinVersionResult(true, null)

        val firstChildVersion = childVersions.firstOrNull()
        if (firstChildVersion != null && inheritedVersion != null && firstChildVersion != inheritedVersion) {
            return@lazy KotlinVersionResult(true, null)
        }

        KotlinVersionResult(false, inheritedVersion ?: firstChildVersion)
    }

    /**
     * Returns true if there is a version conflict between parent/child modules that prevent this module from
     * using _any_ Kotlin version.
     */
    fun hasKotlinVersionConflict(): Boolean {
        return possibleKotlinVersion.conflict
    }

    /**
     * Returns the Kotlin version that has to be used due to constraints by the project (otherwise errors would appear).
     * If there is no restriction, or there is a version conflict (see [hasKotlinVersionConflict]) this function returns null
     */
    fun getForcedKotlinVersion(): IdeKotlinVersion? {
        return possibleKotlinVersion.forcedVersion
    }

    fun addChildren(children: List<KotlinWithGradleModuleNode>) {
        _children.addAll(children)
    }

    fun getNodeForModule(module: Module): KotlinWithGradleModuleNode? {
        if (module == this.module.baseModule) return this
        return children.firstNotNullOfOrNull { it.getNodeForModule(module) }
    }
}


private fun buildHierarchyRecursively(
    currentGradleNode: GradleModuleTreeNode,
    moduleKotlinVersions: Map<Module, IdeKotlinVersion>,
    parent: KotlinWithGradleModuleNode? = null
): KotlinWithGradleModuleNode {
    val currentNode = KotlinWithGradleModuleNode(
        module = currentGradleNode.module,
        parent = parent,
        definedKotlinVersion = moduleKotlinVersions[currentGradleNode.module.baseModule]
    )

    val childNodes = currentGradleNode.children.map {
        buildHierarchyRecursively(it, moduleKotlinVersions, currentNode)
    }
    currentNode.addChildren(childNodes)
    return currentNode
}

internal fun Project.buildKotlinModuleHierarchy(): KotlinWithGradleModuleNode? {
    val allKotlinModulesWithVersion = modules.filter { it.hasKotlinPluginEnabled() }.mapNotNull { module ->
        val version = module.getGradleKotlinVersion() ?: return@mapNotNull null
        val kotlinVersion = IdeKotlinVersion.parse(version).getOrNull() ?: return@mapNotNull null
        module.toModuleGroup().baseModule to kotlinVersion
    }.toMap()

    val rootModule = getRootModule(this) ?: return null
    val gradleHierarchy = buildGradleModuleHierarchy(rootModule) ?: return null

    return buildHierarchyRecursively(gradleHierarchy, allKotlinModulesWithVersion)
}