// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinMppGradleProjectResolverExtensionKt")

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.AbstractDependencyData
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMPPGradleProjectResolver
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.KotlinMppGradleProjectResolverExtension.Result
import org.jetbrains.kotlin.idea.projectModel.KotlinComponent
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData

internal fun KotlinMppGradleProjectResolverExtension.Companion.buildInstance(): KotlinMppGradleProjectResolverExtension =
    CompositeKotlinMppGradleProjectResolverExtension(EP_NAME.extensionList)

private class CompositeKotlinMppGradleProjectResolverExtension(
    private val extensions: List<KotlinMppGradleProjectResolverExtension>
) : KotlinMppGradleProjectResolverExtension {

    override fun beforeMppGradleSourceSetDataNodeCreation(
        context: KotlinMPPGradleProjectResolver.Context,
        component: KotlinComponent
    ): Result {
        return extensions.map { extension -> extension.beforeMppGradleSourceSetDataNodeCreation(context, component) }.reduce()
    }

    override fun afterMppGradleSourceSetDataNodeCreated(
        context: KotlinMPPGradleProjectResolver.Context,
        component: KotlinComponent,
        sourceSetDataNode: DataNode<GradleSourceSetData>
    ) {
        extensions.forEach { extension -> extension.afterMppGradleSourceSetDataNodeCreated(context, component, sourceSetDataNode) }
    }

    override fun beforePopulateContentRoots(
        context: KotlinMPPGradleProjectResolver.Context,
        sourceSetDataNode: DataNode<GradleSourceSetData>,
        sourceSet: KotlinSourceSet
    ): Result {
        return extensions.map { extension -> extension.beforePopulateContentRoots(context, sourceSetDataNode, sourceSet) }.reduce()
    }

    override fun afterPopulateContentRoots(
        context: KotlinMPPGradleProjectResolver.Context,
        sourceSetDataNode: DataNode<GradleSourceSetData>,
        sourceSet: KotlinSourceSet
    ) {
        extensions.forEach { extension -> extension.afterPopulateContentRoots(context, sourceSetDataNode, sourceSet) }
    }

    override fun beforePopulateSourceSetDependencies(
        context: KotlinMPPGradleProjectResolver.Context,
        sourceSetDataNode: DataNode<GradleSourceSetData>,
        sourceSet: KotlinSourceSet,
        dependencies: Set<IdeaKotlinDependency>
    ): Result = extensions
        .map { extension -> extension.beforePopulateSourceSetDependencies(context, sourceSetDataNode, sourceSet, dependencies) }.reduce()

    override fun afterPopulateSourceSetDependencies(
        context: KotlinMPPGradleProjectResolver.Context,
        sourceSetDataNode: DataNode<GradleSourceSetData>,
        sourceSet: KotlinSourceSet,
        dependencies: Set<IdeaKotlinDependency>,
        dependencyNodes: List<DataNode<out AbstractDependencyData<*>>>
    ) {
        extensions.forEach { extension ->
            extension.afterPopulateSourceSetDependencies(context, sourceSetDataNode, sourceSet, dependencies, dependencyNodes)
        }
    }

    override fun provideAdditionalProjectArtifactDependencyResolvers(): List<KotlinProjectArtifactDependencyResolver> {
        return extensions.flatMap { extension -> extension.provideAdditionalProjectArtifactDependencyResolvers() }
    }

    override fun afterResolveFinished(context: KotlinMPPGradleProjectResolver.Context) {
        return extensions.forEach { extension -> extension.afterResolveFinished(context) }
    }
}

private operator fun Result.plus(other: Result) = if (this == Result.Skip) this else other

private fun Iterable<Result>.reduce() = reduceOrNull { acc, result -> acc + result } ?: Result.Proceed
