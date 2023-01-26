// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradleJava.configuration.mpp.KotlinMppGradleProjectResolverExtension.Result
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.projectModel.KotlinComponent
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

internal fun KotlinMppGradleProjectResolverExtension.Companion.beforeMppGradleSourceSetDataNodeCreation(
    model: KotlinMPPGradleModel,
    resolverCtx: ProjectResolverContext,
    gradleModule: IdeaModule,
    moduleDataNode: DataNode<ModuleData>,
    component: KotlinComponent
): Result {
    val context = KotlinMppGradleProjectResolverExtensionContextImpl(model, resolverCtx, gradleModule, moduleDataNode)
    return EP_NAME.extensionList.fold(Result.Proceed) { result, extension ->
        val nextResult = extension.beforeMppGradleSourceSetDataNodeCreation(context, component)
        if (result == Result.Skip || nextResult == Result.Skip) Result.Skip
        else Result.Proceed
    }
}

internal fun KotlinMppGradleProjectResolverExtension.Companion.afterMppGradleSourceSetDataNodeCreated(
    model: KotlinMPPGradleModel,
    resolverCtx: ProjectResolverContext,
    gradleModule: IdeaModule,
    moduleDataNode: DataNode<ModuleData>,
    component: KotlinComponent,
    sourceSetDataNode: DataNode<GradleSourceSetData>
) {
    val context = KotlinMppGradleProjectResolverExtensionContextImpl(model, resolverCtx, gradleModule, moduleDataNode)
    return EP_NAME.extensionList.forEach { extension ->
        extension.afterMppGradleSourceSetDataNodeCreated(context, component, sourceSetDataNode)
    }
}

internal fun KotlinMppGradleProjectResolverExtension.Companion.beforePopulateContentRoots(
    model: KotlinMPPGradleModel,
    resolverCtx: ProjectResolverContext,
    gradleModule: IdeaModule,
    moduleDataNode: DataNode<ModuleData>,
    sourceSetDataNode: DataNode<GradleSourceSetData>,
    sourceSet: KotlinSourceSet
): Result {
    val context = KotlinMppGradleProjectResolverExtensionContextImpl(model, resolverCtx, gradleModule, moduleDataNode)

    return EP_NAME.extensionList.fold(Result.Proceed) { result, extension ->
        val nextResult = extension.beforePopulateContentRoots(context, sourceSetDataNode, sourceSet)
        if (result == Result.Skip || nextResult == Result.Skip) Result.Skip
        else Result.Proceed
    }
}

internal fun KotlinMppGradleProjectResolverExtension.Companion.afterPopulateContentRoots(
    model: KotlinMPPGradleModel,
    resolverCtx: ProjectResolverContext,
    gradleModule: IdeaModule,
    moduleDataNode: DataNode<ModuleData>,
    sourceSetDataNode: DataNode<GradleSourceSetData>,
    sourceSet: KotlinSourceSet
) {
    val context = KotlinMppGradleProjectResolverExtensionContextImpl(model, resolverCtx, gradleModule, moduleDataNode)
    EP_NAME.extensionList.forEach { extension ->
        extension.afterPopulateContentRoots(context, sourceSetDataNode, sourceSet)
    }
}

internal fun KotlinMppGradleProjectResolverExtension.Companion.provideAdditionalProjectArtifactDependencyResolvers()
        : List<KotlinProjectArtifactDependencyResolver> = EP_NAME.extensionList.mapNotNull { extension ->
    extension.provideAdditionalProjectArtifactDependencyResolver()
}
