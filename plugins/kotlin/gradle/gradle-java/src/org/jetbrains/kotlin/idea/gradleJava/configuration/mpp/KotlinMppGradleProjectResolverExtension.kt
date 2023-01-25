// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

interface KotlinMppGradleProjectResolverExtension {
    companion object {
        val EP_NAME = ExtensionPointName.create<KotlinMppGradleProjectResolverExtension>(
            "org.jetbrains.kotlin.mpp.gradleProjectResolverExtension"
        )
    }

    fun shouldSkipDefaultModuleNodeInitialization(
        gradleModule: IdeaModule,
        moduleDataNode: DataNode<ModuleData>,
        projectDataNode: DataNode<ProjectData>,
        mppModel: KotlinMPPGradleModel,
        resolverContext: ProjectResolverContext,
    ): Boolean = false

    fun beforeDefaultModuleNodeInitialization(
        gradleModule: IdeaModule,
        moduleDataNode: DataNode<ModuleData>,
        projectDataNode: DataNode<ProjectData>,
        mppModel: KotlinMPPGradleModel,
        resolverContext: ProjectResolverContext,
    ) {}

    fun afterDefaultModuleNodeInitialization(
        gradleModule: IdeaModule,
        moduleDataNode: DataNode<ModuleData>,
        projectDataNode: DataNode<ProjectData>,
        mppModel: KotlinMPPGradleModel,
        resolverContext: ProjectResolverContext,
    ) {}

    fun shouldSkipDefaultDependencies(
        gradleModule: IdeaModule,
        moduleDataNode: DataNode<ModuleData>,
        projectDataNode: DataNode<ProjectData>,
        mppModel: KotlinMPPGradleModel,
        resolverCtx: ProjectResolverContext,
    ): Boolean = false

    fun beforeDefaultDependencyHandling(
        gradleModule: IdeaModule,
        moduleDataNode: DataNode<ModuleData>,
        projectDataNode: DataNode<ProjectData>,
        mppModel: KotlinMPPGradleModel,
        resolverCtx: ProjectResolverContext,
    ) {}

    fun afterDefaultDependencyHandling(
        gradleModule: IdeaModule,
        moduleDataNode: DataNode<ModuleData>,
        projectDataNode: DataNode<ProjectData>,
        mppModel: KotlinMPPGradleModel,
        resolverCtx: ProjectResolverContext,
    ) {}
}
