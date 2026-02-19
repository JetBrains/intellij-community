// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.kpm

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.Order
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.gradle.configuration.kpm.ModuleDataInitializer
import org.jetbrains.kotlin.idea.gradleJava.configuration.kpm.KotlinKPMGradleProjectResolver.Companion.getIdeaKpmProject
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

@Order(ExternalSystemConstants.UNORDERED)
class KotlinBasicModuleDataInitializer : ModuleDataInitializer {
    override fun initialize(
        gradleModule: IdeaModule,
        mainModuleNode: DataNode<ModuleData>,
        projectDataNode: DataNode<ProjectData>,
        resolverCtx: ProjectResolverContext,
        initializerContext: ModuleDataInitializer.Context
    ) {
        initializerContext.model = resolverCtx.getIdeaKpmProject(gradleModule)
        val mainModuleData = mainModuleNode.data ?: return
        with(mainModuleData) {
            initializerContext.mainModuleConfigPath = linkedExternalProjectPath
            initializerContext.mainModuleFileDirectoryPath = moduleFileDirectoryPath
            initializerContext.moduleGroup = if (!resolverCtx.isUseQualifiedModuleNames) {
                val gradlePath = gradleModule.gradleProject.path
                val isRootModule = gradlePath.isEmpty() || gradlePath == ":"
                if (isRootModule) {
                    arrayOf(internalName)
                } else {
                    gradlePath.split(":").drop(1).toTypedArray()
                }
            } else null

        }
        initializerContext.jdkName = gradleModule.jdkNameIfAny
        with(projectDataNode.data) {
            if (mainModuleData.linkedExternalProjectPath == linkedExternalProjectPath) {
                group = mainModuleData.group
                version = mainModuleData.version
            }
        }
    }
}

private val IdeaModule.jdkNameIfAny
    get() = try {
        jdkName
    } catch (e: UnsupportedMethodException) {
        null
    }
