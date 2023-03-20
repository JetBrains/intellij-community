// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinProjectArtifactDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinSourceDependency
import org.jetbrains.kotlin.gradle.idea.tcs.extras.artifactsClasspath
import org.jetbrains.kotlin.idea.gradle.configuration.kotlinSourceSetData
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMppGradleProjectResolver
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver

interface KotlinProjectArtifactDependencyResolver {
    fun resolve(
        context: KotlinMppGradleProjectResolver.Context,
        sourceSetNode: DataNode<GradleSourceSetData>,
        dependency: IdeaKotlinProjectArtifactDependency
    ): Set<IdeaKotlinSourceDependency>

    companion object {
        internal val key = Key.create<MutableList<KotlinProjectArtifactDependencyResolver>>(
            KotlinProjectArtifactDependencyResolver::class.java.name
        )

        fun register(project: DataNode<out ProjectData>, resolver: KotlinProjectArtifactDependencyResolver) {
            project.putUserDataIfAbsent(key, mutableListOf()).add(resolver)
        }
    }
}

fun KotlinProjectArtifactDependencyResolver(): KotlinProjectArtifactDependencyResolver {
    return KotlinProjectArtifactDependencyResolverImpl()
}

private class KotlinProjectArtifactDependencyResolverImpl : KotlinProjectArtifactDependencyResolver {

    override fun resolve(
        context: KotlinMppGradleProjectResolver.Context,
        sourceSetNode: DataNode<GradleSourceSetData>,
        dependency: IdeaKotlinProjectArtifactDependency
    ): Set<IdeaKotlinSourceDependency> {
        val resolvedByExtensions = context.projectDataNode.getUserData(KotlinProjectArtifactDependencyResolver.key).orEmpty()
            .plus(KotlinMppGradleProjectResolverExtension.buildInstance().provideAdditionalProjectArtifactDependencyResolvers())
            .flatMap { resolver -> resolver.resolve(context, sourceSetNode, dependency) }
            .toSet()

        val sourceSetMap = context.projectDataNode.getUserData(GradleProjectResolver.RESOLVED_SOURCE_SETS).orEmpty()
        val artifactsMap = context.projectDataNode.getUserData(GradleProjectResolver.CONFIGURATION_ARTIFACTS).orEmpty()
        val modulesOutputsMap = context.projectDataNode.getUserData(GradleProjectResolver.MODULES_OUTPUTS).orEmpty()

        return dependency.artifactsClasspath.flatMap { artifactFile ->
            val id = artifactsMap[artifactFile.path] ?: modulesOutputsMap[artifactFile.path]?.first ?: return@flatMap emptySet()
            val sourceSetDataNode = sourceSetMap[id]?.first ?: return@flatMap emptySet()
            val sourceSet = sourceSetMap[id]?.second ?: return@flatMap emptySet()
            val sourceSetNames = sourceSetDataNode.kotlinSourceSetData?.sourceSetInfo?.dependsOn.orEmpty()
                .mapNotNull { dependsOnId -> sourceSetMap[dependsOnId]?.second?.name } + sourceSet.name

            dependency.resolved(sourceSetNames.toSet())
        }.toSet() + resolvedByExtensions
    }
}
