// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KotlinGradleCodeInsightUtils")

package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.WritingAccessProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.PathUtil
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.pathString

fun findGradleProjectStructure(file: PsiFile) =
    ModuleUtilCore.findModuleForFile(file.virtualFile, file.project)?.let { findGradleProjectStructure(it) }

fun findGradleProjectStructure(module: Module): DataNode<ProjectData>? {
    val externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(module) ?: return null
    val projectInfo = ExternalSystemUtil.getExternalProjectInfo(module.project, GradleConstants.SYSTEM_ID, externalProjectPath) ?: return null
    return projectInfo.externalProjectStructure
}

@NonNls
private const val DEFAULT_SCRIPT_NAME = "build.gradle"
@NonNls
private const val SETTINGS_FILE_NAME = "settings.gradle"

@NonNls
private const val KOTLIN_BUILD_SCRIPT_NAME = "build.gradle.kts"
@NonNls
private const val KOTLIN_SETTINGS_SCRIPT_NAME = "settings.gradle.kts"

fun Module.getBuildScriptPsiFile(): PsiFile? {
    return getBuildScriptFile(DEFAULT_SCRIPT_NAME, KOTLIN_BUILD_SCRIPT_NAME)?.getPsiFile(project)
}

fun Module.getBuildScriptSettingsPsiFile(): PsiFile? {
    return getBuildScriptSettingsFile(SETTINGS_FILE_NAME, KOTLIN_SETTINGS_SCRIPT_NAME)?.getPsiFile(project)
}

fun Project.getTopLevelBuildScriptPsiFile(): PsiFile? {
    val projectDir = this.guessProjectDir() ?: return null
    return findBuildGradleFile(projectDir.path, DEFAULT_SCRIPT_NAME, KOTLIN_BUILD_SCRIPT_NAME)?.getPsiFile(this)
}

fun Module.getTopLevelBuildScriptSettingsPsiFile(): PsiFile? {
    val externalProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(this) ?: return null
    return getTopLevelBuildScriptSettingsPsiFile(project, externalProjectPath)
}

fun getTopLevelBuildScriptSettingsPsiFile(project: Project, externalProjectPath: String): PsiFile? {
    return findBuildGradleFile(externalProjectPath, SETTINGS_FILE_NAME, KOTLIN_SETTINGS_SCRIPT_NAME)
        ?.getPsiFile(project)
}

private fun Module.getBuildScriptFile(vararg fileNames: String): Path? {
    moduleNioFile.parent?.let { moduleDir ->
        findBuildGradleFile(moduleDir.pathString, *fileNames)?.let {
            return it
        }
    }

    for (contentRoot in ModuleRootManager.getInstance(this).contentRoots) {
        findBuildGradleFile(contentRoot.path, *fileNames)?.let {
            return it
        }
    }

    ExternalSystemApiUtil.getExternalProjectPath(this)?.let { externalProjectPath ->
        findBuildGradleFile(externalProjectPath, *fileNames)?.let {
            return it
        }
    }

    return null
}

private fun Module.getBuildScriptSettingsFile(vararg fileNames: String): Path? {
    ExternalSystemApiUtil.getExternalProjectPath(this)?.let { externalProjectPath ->
        return generateSequence(externalProjectPath) {
            PathUtil.getParentPath(it).ifBlank { null }
        }.mapNotNull {
            findBuildGradleFile(it, *fileNames)
        }.firstOrNull()
    }

    return null
}

private fun findBuildGradleFile(path: String, vararg fileNames: String): Path? = fileNames.asSequence()
    .map { Path("$path/$it") }
    .firstOrNull(Path::exists)

private fun Path.getPsiFile(project: Project) = VfsUtil.findFile(this, true)?.let {
    PsiManager.getInstance(project).findFile(it)
}

fun PsiFile.canBeConfigured(): Boolean = WritingAccessProvider.isPotentiallyWritable(this.virtualFile, null)