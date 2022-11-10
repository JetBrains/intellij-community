// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.kpm

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.Order
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.gradle.idea.kpm.*
import org.jetbrains.kotlin.idea.base.externalSystem.findAll
import org.jetbrains.kotlin.idea.gradle.configuration.findChildModuleById
import org.jetbrains.kotlin.idea.gradle.configuration.kpm.ModuleDataInitializer
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

@Order(ExternalSystemConstants.UNORDERED + 2)
class KotlinBasicFragmentDataInitializer : ModuleDataInitializer {
    override fun initialize(
        gradleModule: IdeaModule,
        mainModuleNode: DataNode<ModuleData>,
        projectDataNode: DataNode<ProjectData>,
        resolverCtx: ProjectResolverContext,
        initializerContext: ModuleDataInitializer.Context
    ) {
        initializerContext.model?.modules?.flatMap { it.fragments }?.forEach { fragment ->
            val moduleId = calculateKotlinFragmentModuleId(gradleModule, fragment.coordinates, resolverCtx)
            val fragmentGradleSourceSetDataNode = mainModuleNode.findChildModuleById(moduleId)
                ?: error("Cannot find GradleSourceSetData node for fragment '$moduleId'")

            if (fragmentGradleSourceSetDataNode.findAll(KotlinFragmentData.KEY).isNotEmpty()) return@forEach
            val refinesFragmentsIds = fragment.dependencies
                .filterIsInstance<IdeaKpmFragmentDependency>()
                .filter { it.type == IdeaKpmFragmentDependency.Type.Refines }
                .map { calculateKotlinFragmentModuleId(gradleModule, it.coordinates, resolverCtx) }

            // TODO use platformDetails to calculate more precised platform
            KotlinFragmentData(moduleId).apply {
                platform = when {
                    fragment.platforms.all { it.isJvm } -> KotlinPlatform.JVM
                    fragment.platforms.all { it.isJs } -> KotlinPlatform.JS
                    fragment.platforms.all { it.isNative } -> KotlinPlatform.NATIVE
                    else -> platform
                }

                platforms.addAll(fragment.platforms)
                refinesFragmentIds.addAll(refinesFragmentsIds)
                fragmentDependencies.addAll(fragment.dependencies)
                languageSettings = fragment.languageSettings
                fragmentGradleSourceSetDataNode.createChild(KotlinFragmentData.KEY, this)
            }
        }
    }
}
