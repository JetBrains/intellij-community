// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.extensions.gradle

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.util.PathUtil
import com.intellij.util.io.exists
import org.jetbrains.annotations.NonNls
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.pathString

@NonNls private const val DEFAULT_SCRIPT_NAME = "build.gradle"
@NonNls private const val SETTINGS_FILE_NAME = "settings.gradle"

@NonNls private const val KOTLIN_BUILD_SCRIPT_NAME = "build.gradle.kts"
@NonNls private const val KOTLIN_SETTINGS_SCRIPT_NAME = "settings.gradle.kts"

fun Module.getBuildScriptPsiFile() =
    getBuildScriptFile(DEFAULT_SCRIPT_NAME, KOTLIN_BUILD_SCRIPT_NAME)?.getPsiFile(project)

fun Module.getBuildScriptSettingsPsiFile() =
    getBuildScriptSettingsFile(SETTINGS_FILE_NAME, KOTLIN_SETTINGS_SCRIPT_NAME)?.getPsiFile(project)

fun Project.getTopLevelBuildScriptPsiFile() = basePath?.let {
    findBuildGradleFile(it, DEFAULT_SCRIPT_NAME, KOTLIN_BUILD_SCRIPT_NAME)?.getPsiFile(this)
}

fun Module.getTopLevelBuildScriptSettingsPsiFile() =
    ExternalSystemApiUtil.getExternalRootProjectPath(this)?.let { externalProjectPath ->
        findBuildGradleFile(externalProjectPath, SETTINGS_FILE_NAME, KOTLIN_SETTINGS_SCRIPT_NAME)?.getPsiFile(
            project
        )
    }

private fun Module.getBuildScriptFile(vararg fileNames: String): Path? {
    val moduleDir = Path(moduleFilePath).parent.pathString
    findBuildGradleFile(moduleDir, *fileNames)?.let {
        return it
    }

    ModuleRootManager.getInstance(this).contentRoots.forEach { root ->
        findBuildGradleFile(root.path, *fileNames)?.let {
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