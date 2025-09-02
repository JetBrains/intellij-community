// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared

import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.settings.ProjectBuildClasspathManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.kotlin.idea.core.script.dependencies.ScriptAdditionalIdeaDependenciesProvider

class GradleScriptAdditionalIdeaDependenciesProvider : ScriptAdditionalIdeaDependenciesProvider {
    override fun getRelatedModules(file: VirtualFile, project: Project): List<Module> {
        val module = ModuleUtilCore.findModuleForFile(file, project) ?: return emptyList()
        val buildClasspathManager = project.service<ProjectBuildClasspathManager>()
        val virtualFileManager = VirtualFileManager.getInstance()
        val esOptions = ExternalSystemModulePropertyManager.getInstance(module)

        val externalProjectPath = esOptions.getRootProjectPath()
        val linkedProjectPath = esOptions.getLinkedProjectPath()

        val projectBuildClassPath = buildClasspathManager.getProjectBuildClasspath()[externalProjectPath] ?: return emptyList()
        val moduleBuildClassPath = projectBuildClassPath.modulesBuildClasspath[linkedProjectPath] ?: return emptyList()

        return moduleBuildClassPath.entries.asSequence()
            .mapNotNull { it.toNioPathOrNull() }
            .mapNotNull { virtualFileManager.findFileByNioPath(it) }
            .mapNotNull { ModuleUtilCore.findModuleForFile(it, project) }
            .toList()
    }
}