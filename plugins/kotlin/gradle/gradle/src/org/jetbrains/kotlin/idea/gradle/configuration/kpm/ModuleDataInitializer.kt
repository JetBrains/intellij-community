// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradle.configuration.kpm

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.config.ExternalSystemRunTask
import org.jetbrains.kotlin.gradle.idea.kpm.IdeaKpmFragment
import org.jetbrains.kotlin.gradle.idea.kpm.IdeaKpmProject
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

interface ModuleDataInitializer {
    fun initialize(
        gradleModule: IdeaModule,
        mainModuleNode: DataNode<ModuleData>,
        projectDataNode: DataNode<ProjectData>,
        resolverCtx: ProjectResolverContext,
        initializerContext: Context
    )

    interface Context {
        //TODO replace with Key-Value registry with proper visibility and immutability
        var model: IdeaKpmProject?
        var jdkName: String?
        var moduleGroup: Array<String>?
        var mainModuleConfigPath: String?
        var mainModuleFileDirectoryPath: String?
        var sourceSetToRunTasks: Map<IdeaKpmFragment, Collection<ExternalSystemRunTask>>

        companion object {
            @JvmStatic
            val EMPTY: Context
                get() = object : Context {
                    override var model: IdeaKpmProject? = null
                    override var jdkName: String? = null
                    override var moduleGroup: Array<String>? = null
                    override var mainModuleConfigPath: String? = null
                    override var mainModuleFileDirectoryPath: String? = null
                    override var sourceSetToRunTasks: Map<IdeaKpmFragment, Collection<ExternalSystemRunTask>> = emptyMap()
                }
        }
    }

    companion object {
        @JvmField
        val EP_NAME: ExtensionPointName<ModuleDataInitializer> =
            ExtensionPointName.create("org.jetbrains.kotlin.kpm.moduleInitialize")
    }
}
