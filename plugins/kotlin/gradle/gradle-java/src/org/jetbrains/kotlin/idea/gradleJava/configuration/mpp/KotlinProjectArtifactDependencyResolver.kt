// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinProjectArtifactDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinSourceDependency
import org.jetbrains.kotlin.idea.gradle.configuration.kotlinSourceSetData
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver


interface KotlinProjectArtifactDependencyResolver {
    fun resolve(dependency: IdeaKotlinProjectArtifactDependency): Set<IdeaKotlinSourceDependency>

    companion object {
        val key = Key.create<KotlinProjectArtifactDependencyResolver>(KotlinProjectArtifactDependencyResolver::class.java.name)
    }
}

fun KotlinProjectArtifactDependencyResolver(project: DataNode<ProjectData>): KotlinProjectArtifactDependencyResolver {
    project.getUserData(KotlinProjectArtifactDependencyResolver.key)?.let { return it }
    return project.putUserDataIfAbsent(KotlinProjectArtifactDependencyResolver.key, KotlinProjectArtifactDependencyResolverImpl(project))
}

private class KotlinProjectArtifactDependencyResolverImpl(
    private val project: DataNode<ProjectData>
) : KotlinProjectArtifactDependencyResolver {

    override fun resolve(dependency: IdeaKotlinProjectArtifactDependency): Set<IdeaKotlinSourceDependency> {
        val sourceSetMap = project.getUserData(GradleProjectResolver.RESOLVED_SOURCE_SETS).orEmpty()
        val artifactsMap = project.getUserData(GradleProjectResolver.CONFIGURATION_ARTIFACTS).orEmpty()
        val modulesOutputsMap = project.getUserData(GradleProjectResolver.MODULES_OUTPUTS).orEmpty()

        val canonicalPath = dependency.coordinates.artifactFile.normalize().canonicalPath
        val id = artifactsMap[canonicalPath] ?: modulesOutputsMap[canonicalPath]?.first ?: return emptySet()
        val sourceSetDataNode = sourceSetMap[id]?.first ?: return emptySet()
        val sourceSet = sourceSetMap[id]?.second ?: return emptySet()
        val sourceSetNames = sourceSetDataNode.kotlinSourceSetData?.sourceSetInfo?.dependsOn.orEmpty()
            .mapNotNull { dependsOnId -> sourceSetMap[dependsOnId]?.second?.name } + sourceSet.name

        return dependency.resolved(sourceSetNames.toSet())
    }
}
