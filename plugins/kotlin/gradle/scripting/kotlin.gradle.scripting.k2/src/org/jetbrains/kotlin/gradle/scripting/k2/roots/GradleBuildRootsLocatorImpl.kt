// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.roots

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.gradle.scripting.shared.LastModifiedFiles
import org.jetbrains.kotlin.gradle.scripting.shared.kotlinDslScriptsModelImportSupported
import org.jetbrains.kotlin.gradle.scripting.shared.roots.*
import org.jetbrains.kotlin.gradle.scripting.shared.scriptConfigurationsNeedToBeUpdated
import org.jetbrains.kotlin.idea.core.script.scriptingErrorLog
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import java.nio.file.Path
import java.nio.file.Paths

class GradleBuildRootsLocatorImpl(val project: Project, val coroutineScope: CoroutineScope) : GradleBuildRootsLocator(project, coroutineScope) {
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
            removeData(old.pathPrefix)
        }

        updateNotifications { it.startsWith(newRoot.pathPrefix) }
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
          scriptConfigurationsNeedToBeUpdated(project, file)
        }
    }

    private fun addFromSettings(
      data: GradleBuildRootData,
      settings: GradleProjectSettings
    ) = data.copy(projectRoots = data.projectRoots.toSet() + settings.modules)

    private fun tryLoadFromFsCache(settings: GradleProjectSettings, version: String): Imported? {
        return tryCreateImportedRoot(settings.externalProjectPath) {
            GradleBuildRootDataSerializer.Companion.getInstance().read(it)?.let { data ->
                val gradleHome = data.gradleHome
                if (gradleHome.isNotBlank() && GradleInstallationManager.Companion.getGradleVersion(Path.of(gradleHome)) != version) return@let null

                addFromSettings(data, settings)
            }
        }
    }

    override fun getScriptInfo(localPath: String): GradleScriptInfo? = null
}