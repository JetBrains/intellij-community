// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.*
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinResolvedBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinSourceCoordinates
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinSourceDependency
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMPPGradleProjectResolver
import org.jetbrains.kotlin.idea.gradleTooling.IdeaKotlinDependenciesContainer
import org.jetbrains.kotlin.idea.projectModel.KotlinGradlePluginVersionDependentApi
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.util.GradleConstants

@OptIn(KotlinGradlePluginVersionDependentApi::class)
internal fun KotlinMppPopulateModuleDependenciesContext.populateModuleDependenciesByKGP() {
    val dependencies = mppModel.dependencies ?: return

    mppModel.sourceSetsByName.values.forEach { sourceSet ->
        populateSourceDependencies(dependencies, sourceSet)
        populateBinaryDependencies(dependencies, sourceSet)
    }
}

/* Prototypical implementation code below */

private fun KotlinMppPopulateModuleDependenciesContext.populateSourceDependencies(
    container: IdeaKotlinDependenciesContainer, sourceSet: KotlinSourceSet
) {
    val sourceSetDataNode = KotlinMPPGradleProjectResolver
        .getSiblingKotlinModuleData(sourceSet, gradleModule, ideModule, resolverCtx)
        ?.cast<GradleSourceSetData>() ?: return

    container[sourceSet.name].filterIsInstance<IdeaKotlinSourceDependency>().forEach { sourceDependency ->
        val dependencyDataNode = findSourceSetDataNode(ideProject, sourceDependency.coordinates) ?: return@forEach
        KotlinMPPGradleProjectResolver.addDependency(
            fromModule = sourceSetDataNode, toModule = dependencyDataNode, sourceSet.isTestComponent
        )
    }
}

private fun KotlinMppPopulateModuleDependenciesContext.populateBinaryDependencies(
    container: IdeaKotlinDependenciesContainer, sourceSet: KotlinSourceSet
) {
    val sourceSetDataNode = KotlinMPPGradleProjectResolver
        .getSiblingKotlinModuleData(sourceSet, gradleModule, ideModule, resolverCtx)
        ?.cast<GradleSourceSetData>() ?: return

    container[sourceSet.name].filterIsInstance<IdeaKotlinBinaryDependency>().map { binaryDependency ->
        val coordinates = binaryDependency.coordinates

        val libraryData = LibraryData(
            GradleConstants.SYSTEM_ID,
            "${coordinates?.group}:${coordinates?.module}:${coordinates?.version}:${coordinates?.sourceSetName.orEmpty()}"
        )

        if (binaryDependency is IdeaKotlinResolvedBinaryDependency) {
            libraryData.addPath(LibraryPathType.BINARY, binaryDependency.binaryFile.absolutePath)
        }

        val libraryDependencyData = LibraryDependencyData(sourceSetDataNode.data, libraryData, LibraryLevel.MODULE)
        sourceSetDataNode.createChild(ProjectKeys.LIBRARY_DEPENDENCY, libraryDependencyData)
    }
}

private fun findSourceSetDataNode(
    project: DataNode<ProjectData>,
    coordinates: IdeaKotlinSourceCoordinates
): DataNode<GradleSourceSetData>? {
    val gradleProjectId = GradleProjectResolverUtil.getModuleId(coordinates.projectPath, coordinates.projectName)
    val sourceSetId = gradleProjectId + ":" + coordinates.sourceSetName

    val moduleNode = ExternalSystemApiUtil.findAllRecursively(project, ProjectKeys.MODULE)
        .firstOrNull { it.data.id == gradleProjectId } ?: return null

    @Suppress("UNCHECKED_CAST")
    return moduleNode.children.firstOrNull { child ->
        val gradleSourceSetData = child.data as? GradleSourceSetData ?: return@firstOrNull false
        gradleSourceSetData.id == sourceSetId
    } as? DataNode<GradleSourceSetData>
}
