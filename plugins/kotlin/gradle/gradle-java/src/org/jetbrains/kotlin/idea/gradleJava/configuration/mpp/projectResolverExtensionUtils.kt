// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

internal fun shouldSkipMppModuleInitBecauseOfExtensions(
    gradleModule: IdeaModule,
    moduleDataNode: DataNode<ModuleData>,
    projectDataNode: DataNode<ProjectData>,
    mppModel: KotlinMPPGradleModel,
    resolverCtx: ProjectResolverContext,
): Boolean {
    return KotlinMppGradleProjectResolverExtension.EP_NAME.extensions.any { extension ->
        extension.shouldSkipDefaultModuleNodeInitialization(gradleModule, moduleDataNode, projectDataNode, mppModel, resolverCtx)
    }
}

internal fun runPreModuleInitExtensions(
    gradleModule: IdeaModule,
    moduleDataNode: DataNode<ModuleData>,
    projectDataNode: DataNode<ProjectData>,
    mppModel: KotlinMPPGradleModel,
    resolverCtx: ProjectResolverContext,
) {
    KotlinMppGradleProjectResolverExtension.EP_NAME.extensions.forEach { extension ->
        extension.beforeDefaultModuleNodeInitialization(gradleModule, moduleDataNode, projectDataNode, mppModel, resolverCtx)
    }
}

internal fun runPostModuleInitExtensions(
    gradleModule: IdeaModule,
    moduleDataNode: DataNode<ModuleData>,
    projectDataNode: DataNode<ProjectData>,
    mppModel: KotlinMPPGradleModel,
    resolverCtx: ProjectResolverContext,
) {
    KotlinMppGradleProjectResolverExtension.EP_NAME.extensions.forEach { extension ->
        extension.afterDefaultModuleNodeInitialization(gradleModule, moduleDataNode, projectDataNode, mppModel, resolverCtx)
    }
}

internal fun shouldSkipMppDependencyHandlingBecauseOfExtensions(
    gradleModule: IdeaModule,
    moduleDataNode: DataNode<ModuleData>,
    projectDataNode: DataNode<ProjectData>,
    mppModel: KotlinMPPGradleModel,
    resolverCtx: ProjectResolverContext,
): Boolean {
    return KotlinMppGradleProjectResolverExtension.EP_NAME.extensions.any { extension ->
        extension.shouldSkipDefaultDependencies(gradleModule, moduleDataNode, projectDataNode, mppModel, resolverCtx)
    }
}

internal fun runPreDependencyHandlingExtensions(
    gradleModule: IdeaModule,
    moduleDataNode: DataNode<ModuleData>,
    projectDataNode: DataNode<ProjectData>,
    mppModel: KotlinMPPGradleModel,
    resolverCtx: ProjectResolverContext,
) {
    KotlinMppGradleProjectResolverExtension.EP_NAME.extensions.forEach { extension ->
        extension.beforeDefaultDependencyHandling(gradleModule, moduleDataNode, projectDataNode, mppModel, resolverCtx)
    }
}

internal fun runPostDependencyHandlingExtensions(
    gradleModule: IdeaModule,
    moduleDataNode: DataNode<ModuleData>,
    projectDataNode: DataNode<ProjectData>,
    mppModel: KotlinMPPGradleModel,
    resolverCtx: ProjectResolverContext,
) {
    KotlinMppGradleProjectResolverExtension.EP_NAME.extensions.forEach { extension ->
        extension.afterDefaultDependencyHandling(gradleModule, moduleDataNode, projectDataNode, mppModel, resolverCtx)
    }
}
