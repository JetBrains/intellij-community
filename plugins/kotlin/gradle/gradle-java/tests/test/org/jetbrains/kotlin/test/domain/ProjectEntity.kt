// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.test.domain

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.configuration.GRADLE_SYSTEM_ID


class ProjectEntity() {
    lateinit var project: Project
    lateinit var moduleManager: ModuleManager
    var modules: MutableList<ModuleEntity>? = null

    // DSL for definition in code
    fun module(name: String, initFunc: ModuleEntity.() -> Unit) {
        modules?.add(ModuleEntity(name).apply(initFunc))
    }

    companion object {
        // Adapter from Openapi
        fun importFromOpenapiProject(project: Project, projectPath: String): ProjectEntity {
            val moduleManager = ModuleManager.getInstance(project)
            val projectDataNode = ExternalSystemApiUtil.findProjectData(project, GRADLE_SYSTEM_ID, projectPath)

            return ProjectEntity().apply {
                this.project = project
                this.moduleManager = moduleManager
                this.modules = moduleManager.modules.map { ModuleEntity.fromOpenapiModule(it) } as MutableList<ModuleEntity>
            }
        }

        // DSL for definition in code
        fun project(initFunc: ProjectEntity.() -> Unit): ProjectEntity {
            return ProjectEntity().apply {
                initFunc()
                if (modules == null) {
                    modules = mutableListOf()
                }
            }
        }
    }
}