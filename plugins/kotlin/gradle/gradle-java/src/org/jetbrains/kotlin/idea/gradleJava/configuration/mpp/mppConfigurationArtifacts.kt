// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMppGradleProjectResolver
import org.jetbrains.plugins.gradle.model.DefaultExternalProjectDependency
import org.jetbrains.plugins.gradle.model.ExternalDependency
import org.jetbrains.plugins.gradle.model.ExternalProjectDependency
import org.jetbrains.plugins.gradle.model.FileCollectionDependency
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import java.io.File

internal fun Collection<ExternalDependency>.modifyDependenciesOnMppModules(
    ideProject: DataNode<ProjectData>,
    resolverCtx: ProjectResolverContext,
) {
    // Add mpp-artifacts into map used for dependency substitution
    val affiliatedArtifacts = getOrCreateAffiliatedArtifactsMap(ideProject, resolverCtx)
    if (affiliatedArtifacts != null) {
        this.forEach { dependency ->
            val existingArtifactDependencies = dependency.getDependencyArtifacts().map { ExternalSystemApiUtil.normalizePath(it.absolutePath) }
            val dependencies2add = existingArtifactDependencies.flatMap { affiliatedArtifacts[it] ?: emptyList() }
                .filter { !existingArtifactDependencies.contains(it) }
            dependencies2add.forEach {
                dependency.addDependencyArtifactInternal(File(it))
            }
        }
    }
}

private fun ExternalDependency.getDependencyArtifacts(): Collection<File> =
    when (this) {
        is ExternalProjectDependency -> this.projectDependencyArtifacts
        is FileCollectionDependency -> this.files
        else -> emptyList()
    }

private fun getOrCreateAffiliatedArtifactsMap(ideProject: DataNode<ProjectData>, resolverCtx: ProjectResolverContext): Map<String, List<String>>? {
    val mppArtifacts = ideProject.getUserData(KotlinMppGradleProjectResolver.MPP_CONFIGURATION_ARTIFACTS) ?: return null
    val configArtifacts = resolverCtx.artifactsMap
    // All MPP modules are already known, we can fill configurations map
    return HashMap<String, MutableList<String>>().also { newMap ->
        mppArtifacts.forEach { (filePath, moduleIds) ->
            val list2add = ArrayList<String>()
            newMap[filePath] = list2add
            for ((index, module) in moduleIds.withIndex()) {
                if (index == 0) {
                    configArtifacts.storeModuleId(filePath, module)
                } else {
                    val affiliatedFileName = "$filePath-MPP-$index"
                    configArtifacts.storeModuleId(affiliatedFileName, module)
                    list2add.add(affiliatedFileName)
                }
            }
        }
    }
}

private fun ExternalDependency.addDependencyArtifactInternal(file: File) {
    when (this) {
        is DefaultExternalProjectDependency -> this.projectDependencyArtifacts =
            ArrayList<File>(this.projectDependencyArtifacts).also {
                it.add(file)
            }

        is ExternalProjectDependency -> try {
            this.projectDependencyArtifacts.add(file)
        } catch (_: Exception) {
            // ignore
        }

        is FileCollectionDependency -> this.files.add(file)
    }
}
