// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.AbstractDependencyData
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMPPGradleProjectResolver.Context
import org.jetbrains.kotlin.idea.projectModel.KotlinComponent
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData

interface KotlinMppGradleProjectResolverExtension {
    companion object {
        val EP_NAME = ExtensionPointName.create<KotlinMppGradleProjectResolverExtension>("org.jetbrains.kotlin.mppProjectResolve")
    }

    enum class Result {
        Skip, Proceed
    }

    fun beforeMppGradleSourceSetDataNodeCreation(
        context: Context, component: KotlinComponent
    ): Result = Result.Proceed

    fun afterMppGradleSourceSetDataNodeCreated(
        context: Context, component: KotlinComponent, sourceSetDataNode: DataNode<GradleSourceSetData>
    ) = Unit

    fun beforePopulateContentRoots(
        context: Context, sourceSetDataNode: DataNode<GradleSourceSetData>, sourceSet: KotlinSourceSet
    ): Result = Result.Proceed

    fun afterPopulateContentRoots(
        context: Context, sourceSetDataNode: DataNode<GradleSourceSetData>, sourceSet: KotlinSourceSet
    ) = Unit

    fun beforePopulateSourceSetDependencies(
        context: Context,
        sourceSetDataNode: DataNode<GradleSourceSetData>,
        sourceSet: KotlinSourceSet,
        dependencies: Set<IdeaKotlinDependency>
    ): Result = Result.Proceed

    fun afterPopulateSourceSetDependencies(
        context: Context,
        sourceSetDataNode: DataNode<GradleSourceSetData>,
        sourceSet: KotlinSourceSet,
        dependencies: Set<IdeaKotlinDependency>,
        dependencyNodes: List<DataNode<out AbstractDependencyData<*>>>
    ) = Unit

    fun provideAdditionalProjectArtifactDependencyResolvers(): List<KotlinProjectArtifactDependencyResolver> = emptyList()

    fun afterResolveFinished(context: Context) = Unit
}
