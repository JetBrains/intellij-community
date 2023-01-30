// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

fun KotlinMppGradleProjectResolverExtension.Companion.Context(
    model: KotlinMPPGradleModel,
    resolverCtx: ProjectResolverContext,
    gradleModule: IdeaModule,
    moduleDataNode: DataNode<ModuleData>
): KotlinMppGradleProjectResolverExtension.Context = KotlinMppGradleProjectResolverExtensionContextImpl(
    model, resolverCtx, gradleModule, moduleDataNode
)

private class KotlinMppGradleProjectResolverExtensionContextImpl(
    override val model: KotlinMPPGradleModel,
    override val resolverCtx: ProjectResolverContext,
    override val gradleModule: IdeaModule,
    override val moduleDataNode: DataNode<ModuleData>
) : KotlinMppGradleProjectResolverExtension.Context
