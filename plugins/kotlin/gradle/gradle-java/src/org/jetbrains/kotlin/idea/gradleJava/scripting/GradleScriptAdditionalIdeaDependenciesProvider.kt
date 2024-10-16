// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.scripting

import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.kotlin.idea.core.script.dependencies.ScriptAdditionalIdeaDependenciesProvider
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings

class GradleScriptAdditionalIdeaDependenciesProvider : ScriptAdditionalIdeaDependenciesProvider() {
    override fun getRelatedModules(file: VirtualFile, project: Project): List<Module> {
        val module = ModuleUtilCore.findModuleForFile(file, project) ?: return emptyList()
        val localSettings = GradleLocalSettings.getInstance(project)
        val virtualFileManager = VirtualFileManager.getInstance()
        val esOptions = ExternalSystemModulePropertyManager.getInstance(module)

        val externalProjectPath = esOptions.getRootProjectPath()
        val linkedProjectPath = esOptions.getLinkedProjectPath()

        val projectBuildClassPath = localSettings.projectBuildClasspath[externalProjectPath] ?: return emptyList()
        val moduleBuildClassPath = projectBuildClassPath.modulesBuildClasspath[linkedProjectPath] ?: return emptyList()

        return moduleBuildClassPath.entries.asSequence()
            .mapNotNull { it.toNioPathOrNull() }
            .mapNotNull { virtualFileManager.findFileByNioPath(it) }
            .mapNotNull { ModuleUtilCore.findModuleForFile(it, project) }
            .toList()
    }

    override fun getRelatedLibraries(file: VirtualFile, project: Project): List<Library> {
        return emptyList()
    }
}