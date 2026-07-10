// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared.roots

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotifications
import java.util.concurrent.ConcurrentHashMap

const val gradleWrapperEnding: String = "/gradle/wrapper/gradle-wrapper.properties"

@Service(Service.Level.PROJECT)
class GradleBuildRootsLocator(private val project: Project) {
    private val importingRoots = ConcurrentHashMap<String, GradleBuildRoot.ImportingStatus>()

    fun maybeAffectedGradleProjectFile(filePath: String): Boolean =
        filePath.endsWith("/gradle.properties") ||
                filePath.endsWith("/gradle.local") ||
                filePath.endsWith(gradleWrapperEnding) ||
                filePath.endsWith(".gradle.kts")

    fun markImportingInProgress(workingDir: String, inProgress: Boolean = true) {
        if (inProgress) {
            importingRoots[workingDir] = GradleBuildRoot.ImportingStatus.IMPORTING
        } else {
            importingRoots.remove(workingDir)
        }
        updateNotifications { it.startsWith(workingDir) }
    }

    fun getImportingStatus(workingDir: String): GradleBuildRoot.ImportingStatus {
        return importingRoots[workingDir] ?: GradleBuildRoot.ImportingStatus.UPDATED
    }

    fun remove(rootPath: String) {
        importingRoots.remove(rootPath)
        updateNotifications { it.startsWith(rootPath) }
    }

    fun updateNotifications(shouldUpdatePath: (String) -> Boolean) {
        if (!project.isOpen) return

        val openedScripts = FileEditorManager.getInstance(project).selectedEditors
            .mapNotNull { it.file }
            .filter {
                shouldUpdatePath(it.path) && maybeAffectedGradleProjectFile(it.path)
            }

        if (openedScripts.isEmpty()) return

        EditorNotifications.getInstance(project).updateAllNotifications()
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): GradleBuildRootsLocator = project.service()
    }
}