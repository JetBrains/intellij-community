// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k1.configuration

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.script.k1.ucache.findDependentScripts
import org.jetbrains.kotlin.idea.core.script.k1.ucache.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.k1.ucache.KotlinScriptLibraryRootTypeId
import org.jetbrains.kotlin.idea.core.script.k1.ucache.modifyKotlinScriptLibraryEntity
import java.util.function.Function
import javax.swing.JComponent

class IndexScriptDependenciesSourcesNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        return if (!FileTypeRegistry.getInstance().isFileOfType(file, JavaClassFileType.INSTANCE) || !file.isScriptDependency(project)) {
            null
        } else {
            Function { fileEditor: FileEditor -> createNotification(fileEditor, project) }
        }
    }

    private fun VirtualFile.findLibsWithSources(project: Project): List<KotlinScriptLibraryEntity>? {
        val storage = WorkspaceModel.getInstance(project).currentSnapshot
        return findDependentScripts(project)
            ?.flatMap { script ->
                script.dependencies
                    .map { storage.resolve(it) ?: error("Can't resolve: ${it.name}") }
                    .filter { lib -> lib.roots.any { it.type == KotlinScriptLibraryRootTypeId.SOURCES } }
            }
    }

    private fun createNotification(fileEditor: FileEditor, project: Project): EditorNotificationPanel =
        EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info).apply {
            text = KotlinBundle.message("kotlin.script.sources.not.yet.indexed")
            createActionLabel(KotlinBundle.message("kotlin.script.sources.index")) {
                fileEditor.file?.findLibsWithSources(project)?.let { libsToIndex ->
                    runWriteAction {
                        WorkspaceModel.getInstance(project).updateProjectModel("Marking sources to index...") { storage ->
                            libsToIndex.forEach {
                                storage.modifyKotlinScriptLibraryEntity(it) { indexSourceRoots = true }
                            }
                        }
                    }
                }

                fileEditor.file?.let { FileEditorManager.getInstance(project).closeFile(it) }
            }

        }
}

private fun VirtualFile.isScriptDependency(project: Project): Boolean = findDependentScripts(project)?.isNotEmpty() == true