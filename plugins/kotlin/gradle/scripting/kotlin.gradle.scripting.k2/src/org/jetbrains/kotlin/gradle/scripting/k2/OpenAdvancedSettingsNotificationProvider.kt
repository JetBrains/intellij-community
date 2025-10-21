// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import KotlinGradleScriptingBundle
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationPanel.Status
import com.intellij.ui.EditorNotificationProvider
import org.jetbrains.kotlin.gradle.scripting.shared.KotlinGradleScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity
import java.util.function.Function
import javax.swing.JComponent

class OpenAdvancedSettingsNotificationProvider() : EditorNotificationProvider {
    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent>? {
        if (!FileTypeRegistry.getInstance().isFileOfType(file, JavaClassFileType.INSTANCE)) return null


        if (AdvancedSettings.getBoolean("gradle.attach.scripts.dependencies.sources")
            || !file.isGradleScriptDependency(project)
        ) return null

        return Function { fileEditor ->
            EditorNotificationPanel(fileEditor, Status.Info).apply {
                text(KotlinGradleScriptingBundle.message("label.sources.were.not.indexed"))
                createActionLabel(KotlinGradleScriptingBundle.message("open.advanced.settings")) {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, AdvancedSettingsConfigurable::class.java)
                }
            }
        }
    }

    private fun VirtualFile.isGradleScriptDependency(project: Project): Boolean {
        val workspaceModel = WorkspaceModel.getInstance(project)
        val index = workspaceModel.currentSnapshot.getVirtualFileUrlIndex()
        val fileUrlManager = workspaceModel.getVirtualFileUrlManager()

        var currentFile: VirtualFile? = this
        while (currentFile != null) {
            val entities =
                index.findEntitiesByUrl(fileUrlManager.getOrCreateFromUrl(currentFile.url)).filterIsInstance<KotlinScriptLibraryEntity>()
            if (entities.none()) {
                currentFile = currentFile.parent
                continue
            }

            return entities.firstOrNull { it.entitySource is KotlinGradleScriptEntitySource } != null
        }

        return false
    }
}



