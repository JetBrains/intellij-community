// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.inspections

import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.kotlin.idea.gradle.inspections.KotlinGradleModelFacade

class DefaultGradleModelFacade : KotlinGradleModelFacade {
    override fun getResolvedVersionByModuleData(moduleData: DataNode<*>, groupId: String, libraryIds: List<String>): String? {
        for (libraryDependencyData in ExternalSystemApiUtil.findAllRecursively(moduleData, ProjectKeys.LIBRARY_DEPENDENCY)) {
            for (libraryId in libraryIds) {
                val libraryNameMarker = "$groupId:$libraryId:"
                if (libraryDependencyData.data.externalName.startsWith(libraryNameMarker)) {
                    return libraryDependencyData.data.externalName.substringAfter(libraryNameMarker)
                }
            }
        }
        return null
    }

    override fun getDependencyModules(ideModule: DataNode<ModuleData>, gradleIdeaProject: IdeaProject): Collection<DataNode<ModuleData>> {
        @Suppress("UNCHECKED_CAST") val ideProject = ideModule.parent as DataNode<ProjectData>
        val dependencyModuleNames =
            ExternalSystemApiUtil.getChildren(ideModule, ProjectKeys.MODULE_DEPENDENCY).map { it.data.target.externalName }.toHashSet()
        return findModulesByNames(dependencyModuleNames, gradleIdeaProject, ideProject)
    }
}

fun DataNode<*>.getResolvedVersionByModuleData(groupId: String, libraryIds: List<String>): String? {
    return KotlinGradleModelFacade.EP_NAME.extensions.asSequence()
        .mapNotNull { it.getResolvedVersionByModuleData(this, groupId, libraryIds) }
        .firstOrNull()
}

fun getDependencyModules(moduleData: DataNode<ModuleData>, gradleIdeaProject: IdeaProject): Collection<DataNode<ModuleData>> {
    @Suppress("DEPRECATION")
    val extensions = Extensions.getExtensions(KotlinGradleModelFacade.EP_NAME)

    for (modelFacade in extensions) {
        val dependencies = modelFacade.getDependencyModules(moduleData, gradleIdeaProject)
        if (dependencies.isNotEmpty()) {
            return dependencies
        }
    }
    return emptyList()
}

// Removing the 'gradleIdeaProject' parameter, removing it breaks importer for some reason
fun findModulesByNames(
    dependencyModuleNames: Set<String>,
    @Suppress("UNUSED_PARAMETER") gradleIdeaProject: IdeaProject,
    ideProject: DataNode<ProjectData>
): LinkedHashSet<DataNode<ModuleData>> {
    val modules = ExternalSystemApiUtil.getChildren(ideProject, ProjectKeys.MODULE)
    return modules.filterTo(LinkedHashSet()) { it.data.externalName in dependencyModuleNames }
}
