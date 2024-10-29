// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradleJava.scripting

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.FsRoot
import org.jetbrains.kotlin.idea.base.projectStructure.externalProjectPath
import org.jetbrains.kotlin.idea.base.projectStructure.productionSourceInfo
import org.jetbrains.kotlin.idea.core.script.dependencies.ScriptAdditionalIdeaDependenciesProvider
import org.jetbrains.kotlin.idea.util.sourceRoots
import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

class GradleScriptAdditionalIdeaDependenciesProvider : ScriptAdditionalIdeaDependenciesProvider() {
    /*
        We try to find applicable gradle buildSrc-like modules with sources
     */
    override fun getRelatedModules(file: VirtualFile, project: Project): List<Module> {
        val gradleSettings = ExternalSystemApiUtil.getSettings(project, GradleConstants.SYSTEM_ID)

        val projectSettings = gradleSettings.linkedProjectsSettings.filterIsInstance<GradleProjectSettings>().firstOrNull()
            ?: return emptyList()

        val includedModulesPath: List<String> = projectSettings.compositeBuild?.compositeParticipants?.filter { part ->
            projectSettings.modules.any { it == part.rootPath }
        }?.flatMap { it.projects } ?: emptyList()

        val includedModulesBuildSrcPaths = includedModulesPath.map { "$it/buildSrc" }

        val rootBuildSrcPath = "${projectSettings.externalProjectPath}/buildSrc"

        val pathsToGradleSpecialModules = (includedModulesPath + includedModulesBuildSrcPaths + rootBuildSrcPath).toSet()

        val gradleModulesWithSources =
            ModuleManager.getInstance(project).modules
                .filter {
                    pathsToGradleSpecialModules.contains(ExternalSystemApiUtil.getExternalProjectPath(it))
                            && it.productionSourceInfo != null
                }

        val gradleModuleClasspath = ModuleUtilCore.findModuleForFile(file, project)?.externalProjectPath?.let {
            GradleBuildClasspathManager.getInstance(project).getModuleClasspathEntries(it).filter { file -> file !is FsRoot }
        }?.toSet() ?: emptySet()

        return gradleModulesWithSources.filter { gradleModule ->
            gradleModule.sourceRoots.intersect(gradleModuleClasspath).isNotEmpty()
        }
    }

    override fun getRelatedLibraries(file: VirtualFile, project: Project): List<Library> {
        return emptyList()
    }
}