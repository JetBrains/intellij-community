// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.util.Key
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradleJava.configuration.KotlinMppGradleProjectResolver
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.psi.UserDataProperty
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

internal var DataNode<out ModuleData>.kotlinMppGradleProjectResolverContext by UserDataProperty(
    Key.create<KotlinMppGradleProjectResolver.Context>("kotlinMppGradleProjectResolverExtensionContext")
)

fun KotlinMppGradleProjectResolver.Companion.Context(
    model: KotlinMPPGradleModel,
    resolverCtx: ProjectResolverContext,
    gradleModule: IdeaModule,
    projectDataNode: DataNode<ProjectData>,
    moduleDataNode: DataNode<ModuleData>
): KotlinMppGradleProjectResolver.Context = KotlinMPPGradleProjectResolverContextImpl(
    model, resolverCtx, gradleModule, projectDataNode, moduleDataNode
)

private class KotlinMPPGradleProjectResolverContextImpl(
    override val mppModel: KotlinMPPGradleModel,
    override val resolverCtx: ProjectResolverContext,
    override val gradleModule: IdeaModule,
    override val projectDataNode: DataNode<ProjectData>,
    override val moduleDataNode: DataNode<ModuleData>
) : KotlinMppGradleProjectResolver.Context
