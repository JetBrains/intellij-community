/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.configuration.multiplatform

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.config.ExternalSystemRunTask
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

interface ModuleDataInitializer {
    fun doInitialize(
        gradleModule: IdeaModule,
        mainModuleNode: DataNode<ModuleData>,
        projectDataNode: DataNode<ProjectData>,
        resolverCtx: ProjectResolverContext
    )
}

interface ComposableInitializeModuleDataAction : ModuleDataInitializer {
    private fun composeWith(lazyAfter: () -> ModuleDataInitializer): ComposableInitializeModuleDataAction =
        object : ComposableInitializeModuleDataAction {
            override fun doInitialize(
                gradleModule: IdeaModule,
                mainModuleNode: DataNode<ModuleData>,
                projectDataNode: DataNode<ProjectData>,
                resolverCtx: ProjectResolverContext
            ) {
                this@ComposableInitializeModuleDataAction.doInitialize(gradleModule, mainModuleNode, projectDataNode, resolverCtx)
                lazyAfter().doInitialize(gradleModule, mainModuleNode, projectDataNode, resolverCtx)
            }
        }

    operator fun plus(next: ComposableInitializeModuleDataAction): ComposableInitializeModuleDataAction = composeWith { next }
}

class InitializeModuleDataContext<TSourceSet>(
    internal var jdkName: String? = null,
    internal var moduleGroup: Array<String>? = null,
) {
    internal lateinit var mainModuleData: ModuleData
    internal lateinit var mainModuleConfigPath: String
    internal lateinit var mainModuleFileDirectoryPath: String
    internal lateinit var sourceSetToRunTasks: Map<TSourceSet, Collection<ExternalSystemRunTask>>
}
