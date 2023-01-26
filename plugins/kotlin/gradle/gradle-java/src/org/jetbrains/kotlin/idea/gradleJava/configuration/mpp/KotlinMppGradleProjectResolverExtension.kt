// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.projectModel.KotlinComponent
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

interface KotlinMppGradleProjectResolverExtension {
    companion object {
        val EP_NAME = ExtensionPointName.create<KotlinMppGradleProjectResolverExtension>(
            "org.jetbrains.kotlin.mppProjectResolve"
        )
    }

    enum class Result {
        Skip, Proceed
    }

    interface Context {
        val model: KotlinMPPGradleModel
        val resolverCtx: ProjectResolverContext
        val gradleModule: IdeaModule
        val moduleDataNode: DataNode<ModuleData>
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

    fun provideAdditionalProjectArtifactDependencyResolver(): KotlinProjectArtifactDependencyResolver? = null
}
