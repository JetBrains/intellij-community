// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.roots

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.gradle.scripting.shared.kotlinDslScriptsModelImportSupported
import org.jetbrains.kotlin.gradle.scripting.shared.roots.AbstractGradleBuildRootDataSerializer
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRoot
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootData
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootsLocator
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleScriptInfo
import org.jetbrains.kotlin.gradle.scripting.shared.roots.Imported
import org.jetbrains.kotlin.gradle.scripting.shared.roots.Legacy
import org.jetbrains.kotlin.gradle.scripting.shared.roots.New
import org.jetbrains.kotlin.gradle.scripting.shared.runPartialGradleImport
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import java.nio.file.Path

class GradleBuildRootsLocatorImpl(val project: Project, val coroutineScope: CoroutineScope) : GradleBuildRootsLocator(project) {
    override fun loadLinkedRoot(settings: GradleProjectSettings, version: String): GradleBuildRoot {
        val supported = kotlinDslScriptsModelImportSupported(version)

        return if (supported) {
          tryLoadFromFsCache(settings, version) ?: New(settings)
        } else {
          Legacy(settings)
        }
    }

    override fun add(newRoot: GradleBuildRoot) {
        val old = roots.add(newRoot)
        if (old is Imported && newRoot !is Imported) {
            removeData(old.externalProjectPath)
        }

        updateNotifications { it.startsWith(newRoot.externalProjectPath) }
    }

    override fun remove(rootPath: String) {
        val removed = roots.remove(rootPath)
        if (removed is Imported) {
            removeData(rootPath)
        }

        updateNotifications { it.startsWith(rootPath) }
    }

    override fun updateNotifications(unused: Boolean, shouldUpdatePath: (String) -> Boolean) {
        if (!project.isOpen) return

        // import notification is a balloon, so should be shown only for selected editor
        FileEditorManager.getInstance(project).selectedEditor?.file?.let {
            if (shouldUpdatePath(it.path) && maybeAffectedGradleProjectFile(it.path)) {
                updateFloatingAction(it)
            }
        }

        val openedScripts = FileEditorManager.getInstance(project).selectedEditors
            .mapNotNull { it.file }
            .filter {
                shouldUpdatePath(it.path) && maybeAffectedGradleProjectFile(it.path)
            }

        if (openedScripts.isEmpty()) return

        EditorNotifications.getInstance(project).updateAllNotifications()
    }

    private fun updateFloatingAction(file: VirtualFile) {
        if (isConfigurationOutOfDate(file)) {
            getInstance(project).getScriptInfo(file)?.buildRoot?.let {
                runPartialGradleImport(project, it)
            }
        }
    }

    private fun addFromSettings(
      data: GradleBuildRootData,
      settings: GradleProjectSettings
    ) = data.copy(projectRoots = data.projectRoots.toSet() + settings.modules)

    private fun tryLoadFromFsCache(settings: GradleProjectSettings, version: String): Imported? {
        return tryCreateImportedRoot(settings.externalProjectPath) {
            AbstractGradleBuildRootDataSerializer.Companion.getInstance().read(it)?.let { data ->
                val gradleHome = data.gradleHome
                if (gradleHome.isNotBlank() && GradleInstallationManager.Companion.getGradleVersion(Path.of(gradleHome)) != version) return@let null

                addFromSettings(data, settings)
            }
        }
    }

    override fun getScriptInfo(localPath: String): GradleScriptInfo? = null
}