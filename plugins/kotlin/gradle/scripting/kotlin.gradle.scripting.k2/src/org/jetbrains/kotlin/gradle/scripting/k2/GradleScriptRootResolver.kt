// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.gradle.scripting.shared.getGradleVersion
import org.jetbrains.kotlin.gradle.scripting.shared.kotlinDslScriptsModelImportSupported
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRoot
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootsLocator
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

internal object GradleScriptRootResolver {
    enum class NotificationKind {
        DONT_CARE,
        OUTSIDE_ANYTHING,
        WAS_NOT_IMPORTED_AFTER_CREATION,
    }

    class ScriptUnderRoot(
        val filePath: String,
        val nearest: GradleBuildRoot?,
    ) {
        val notificationKind: NotificationKind
            get() = when {
                nearest == null -> NotificationKind.OUTSIDE_ANYTHING
                nearest.isImportingInProgress() -> NotificationKind.DONT_CARE
                !nearest.supportsScriptModelImport -> NotificationKind.DONT_CARE
                else -> NotificationKind.WAS_NOT_IMPORTED_AFTER_CREATION
            }
    }

    fun findScriptBuildRoot(project: Project, gradleKtsFile: VirtualFile): ScriptUnderRoot? {
        val filePath = gradleKtsFile.path
        if (project.isDisposed) {
            // This is not really correct as this check should be under a read/write action. Still, better than nothing.
            return null
        }

        if (!filePath.endsWith(".gradle.kts")) return null

        val linkedRoots = getLinkedRoots(project)
        if (filePath.endsWith("/build.gradle.kts") ||
            filePath.endsWith("/settings.gradle.kts") ||
            filePath.endsWith("/init.gradle.kts")
        ) {
            // build|settings|init.gradle.kts scripts should be located near gradle project root only
            linkedRoots.firstOrNull { filePath.substringBeforeLast("/") in it.projectRoots }?.let {
                return ScriptUnderRoot(filePath, it)
            }
        }

        // other scripts: "included", "precompiled" scripts, scripts in unlinked projects,
        // or just random files with ".gradle.kts" ending OR scripts those Gradle has not provided
        val nearest = linkedRoots
            .filter { root -> filePath.startsWith(root.externalProjectPath) }
            .maxByOrNull { root -> root.externalProjectPath.length }

        return ScriptUnderRoot(filePath, nearest = nearest)
    }

    private fun getLinkedRoots(project: Project): List<GradleBuildRoot> {
        return getGradleSettings(project).linkedProjectsSettings.map { settings ->
            loadLinkedRoot(project, settings)
        }
    }

    private fun loadLinkedRoot(project: Project, settings: GradleProjectSettings): GradleBuildRoot {
        val version = getGradleVersion(project, settings)
        val importingStatus = GradleBuildRootsLocator.getInstance(project).getImportingStatus(settings.externalProjectPath)
        val supportsScriptModelImport = kotlinDslScriptsModelImportSupported(version)
        val projectRoots = if (supportsScriptModelImport) {
            getProjectRootsFromWorkspaceModel(project, settings) ?: settings.fallbackProjectRoots()
        }
        else {
            settings.fallbackProjectRoots()
        }

        return GradleBuildRoot(settings.externalProjectPath, projectRoots, supportsScriptModelImport, importingStatus)
    }

    private fun getProjectRootsFromWorkspaceModel(
        project: Project,
        settings: GradleProjectSettings,
    ): Collection<String>? {
        val scriptRoots = GradleWorkspaceScriptRootsProvider.getImportedScriptRoots(project, settings.externalProjectPath)
        if (scriptRoots.isEmpty()) return null

        val projectRoots = GradleWorkspaceScriptRootsProvider.getImportedProjectRoots(project, settings.externalProjectPath)
            .takeIf { it.isNotEmpty() }
            ?: settings.fallbackProjectRoots()

        return scriptRoots + projectRoots
    }

    private fun GradleProjectSettings.fallbackProjectRoots(): Collection<String> {
        return modules.takeIf { it.isNotEmpty() } ?: listOf(externalProjectPath)
    }

    private fun getGradleSettings(project: Project): GradleSettings {
        return ExternalSystemApiUtil.getSettings(project, GradleConstants.SYSTEM_ID) as GradleSettings
    }
}
