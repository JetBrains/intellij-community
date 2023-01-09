// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinProjectArtifactDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinSourceDependency
import org.jetbrains.kotlin.gradle.idea.tcs.extras.artifactsClasspath
import org.jetbrains.kotlin.idea.gradle.configuration.kotlinSourceSetData
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver


interface KotlinProjectArtifactDependencyResolver {
    fun resolve(dependency: IdeaKotlinProjectArtifactDependency): Set<IdeaKotlinSourceDependency>

    companion object {
        internal val key = Key.create<MutableList<KotlinProjectArtifactDependencyResolver>>(
            KotlinProjectArtifactDependencyResolver::class.java.name
        )

        fun register(project: DataNode<out ProjectData>, resolver: KotlinProjectArtifactDependencyResolver) {
            project.putUserDataIfAbsent(key, mutableListOf()).add(resolver)
        }
    }
}

fun KotlinProjectArtifactDependencyResolver(project: DataNode<ProjectData>): KotlinProjectArtifactDependencyResolver {
    return KotlinProjectArtifactDependencyResolverImpl(project)
}

private class KotlinProjectArtifactDependencyResolverImpl(
    private val project: DataNode<ProjectData>
) : KotlinProjectArtifactDependencyResolver {

    override fun resolve(dependency: IdeaKotlinProjectArtifactDependency): Set<IdeaKotlinSourceDependency> {
        val resolvedByExtensions = project.getUserData(KotlinProjectArtifactDependencyResolver.key).orEmpty()
            .flatMap { resolver -> resolver.resolve(dependency) }
            .toSet()

        val sourceSetMap = project.getUserData(GradleProjectResolver.RESOLVED_SOURCE_SETS).orEmpty()
        val artifactsMap = project.getUserData(GradleProjectResolver.CONFIGURATION_ARTIFACTS).orEmpty()
        val modulesOutputsMap = project.getUserData(GradleProjectResolver.MODULES_OUTPUTS).orEmpty()

        return dependency.artifactsClasspath.flatMap { artifactFile ->
            val id = artifactsMap[artifactFile.path] ?: modulesOutputsMap[artifactFile.path]?.first ?: return emptySet()
            val sourceSetDataNode = sourceSetMap[id]?.first ?: return emptySet()
            val sourceSet = sourceSetMap[id]?.second ?: return emptySet()
            val sourceSetNames = sourceSetDataNode.kotlinSourceSetData?.sourceSetInfo?.dependsOn.orEmpty()
                .mapNotNull { dependsOnId -> sourceSetMap[dependsOnId]?.second?.name } + sourceSet.name

            dependency.resolved(sourceSetNames.toSet())
        }.toSet() + resolvedByExtensions
    }
}
