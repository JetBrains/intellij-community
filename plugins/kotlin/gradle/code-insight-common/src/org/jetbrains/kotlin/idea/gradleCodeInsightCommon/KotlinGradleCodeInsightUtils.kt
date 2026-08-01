// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("KotlinGradleCodeInsightUtils")

package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.WritingAccessProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.PathUtil
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.gradleIdentityPathOrNull
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

/**
 * A Gradle project in the ancestor chain of the project being configured.
 *
 * @property externalProjectPath the project directory (its `build.gradle[.kts]` location).
 * @property isSelf `true` for the configured project itself (which only inherits repositories from its own
 * `allprojects` block), `false` for a strict ancestor (which contributes both `allprojects` and `subprojects`).
 */
data class GradleProjectHierarchyEntry(val externalProjectPath: String, val isSelf: Boolean)

/**
 * Returns the root-to-self chain of Gradle projects whose `allprojects` / `subprojects` blocks can supply repositories to the
 * project located at [externalProjectPath]: the project itself plus every ancestor up to the root of [externalRootProjectPath]'s build.
 *
 * The hierarchy is derived from the logical Gradle identity paths (e.g. `:app:feature`) rather than the physical
 * directory layout, so it stays correct even when `projectDir` is remapped in `settings.gradle`. Ancestry never
 * crosses the build boundary, because all projects of a single build share one [DataNode] structure.
 *
 * Returns `null` when the Gradle project model is not available yet (e.g. before the first import).
 */
fun collectGradleProjectHierarchy(
    project: Project,
    externalRootProjectPath: String,
    externalProjectPath: String,
): List<GradleProjectHierarchyEntry>? {
    val projectInfo = ExternalSystemUtil.getExternalProjectInfo(project, GradleConstants.SYSTEM_ID, externalRootProjectPath) ?: return null
    val structure = projectInfo.externalProjectStructure ?: return null
    val moduleNodes = ExternalSystemApiUtil.getChildren(structure, ProjectKeys.MODULE)
    val selfIdentityPath = moduleNodes
        .firstOrNull { FileUtil.pathsEqual(it.data.linkedExternalProjectPath, externalProjectPath) }
        ?.data?.gradleIdentityPathOrNull
        ?: return null

    return moduleNodes.mapNotNull { node ->
        val identityPath = node.data.gradleIdentityPathOrNull ?: return@mapNotNull null
        if (!isGradleAncestorOrSelf(ancestorIdentityPath = identityPath, identityPath = selfIdentityPath)) return@mapNotNull null
        identityPath to GradleProjectHierarchyEntry(node.data.linkedExternalProjectPath, isSelf = identityPath == selfIdentityPath)
    }.sortedBy { (identityPath, _) -> identityPath.identityPathDepth() }
        .map { (_, entry) -> entry }
}

/**
 * Checks whether the project with [ancestorIdentityPath] is an ancestor of (or the same as) the project with
 * [identityPath], comparing logical Gradle paths such as `:app:feature`.
 */
private fun isGradleAncestorOrSelf(ancestorIdentityPath: String, identityPath: String): Boolean {
    if (ancestorIdentityPath == identityPath) return true
    if (ancestorIdentityPath == ":") return true // the root project is an ancestor of every project in the build
    return identityPath.startsWith("$ancestorIdentityPath:")
}

private fun String.identityPathDepth(): Int = if (this == ":") 0 else count { it == ':' }

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

fun Module.getTopLevelBuildScriptPsiFile(): PsiFile? {
    val externalProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(this) ?: return null
    return findBuildGradleFile(externalProjectPath, DEFAULT_SCRIPT_NAME, KOTLIN_BUILD_SCRIPT_NAME)?.getPsiFile(project)
}

fun getBuildScriptPsiFile(project: Project, externalProjectPath: String): PsiFile? {
    return findBuildGradleFile(externalProjectPath, DEFAULT_SCRIPT_NAME, KOTLIN_BUILD_SCRIPT_NAME)?.getPsiFile(project)
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