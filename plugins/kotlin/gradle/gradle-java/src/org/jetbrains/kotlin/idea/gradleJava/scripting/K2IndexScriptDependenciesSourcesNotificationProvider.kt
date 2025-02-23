// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.scripting

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.core.script.scriptConfigurationsSourceOfType
import org.jetbrains.kotlin.idea.gradleJava.KotlinGradleJavaBundle
import org.jetbrains.kotlin.idea.gradleJava.scripting.GradleScriptConfigurationsSource.KotlinGradleScriptModuleEntitySource
import java.util.function.Function
import javax.swing.JComponent

@InternalIgnoreDependencyViolation
class K2IndexScriptDependenciesSourcesNotificationProvider : EditorNotificationProvider {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (Registry.`is`("kotlin.scripting.index.dependencies.sources")) return null

    return if (FileTypeRegistry.getInstance().isFileOfType(file, JavaClassFileType.INSTANCE) && file.isScriptDependency(project)) {
      Function { fileEditor: FileEditor -> createNotification(fileEditor, project) }
    }
    else {
      null
    }
  }

  private fun createNotification(fileEditor: FileEditor, project: Project): EditorNotificationPanel =
    EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info).apply {
      text = KotlinBundle.message("kotlin.script.sources.not.yet.indexed")
      createActionLabel(KotlinBundle.message("kotlin.script.sources.index")) {
        Registry.get("kotlin.scripting.index.dependencies.sources").setValue(true)
        CoroutineScopeService.getCoroutineScope(project).launch {
          withBackgroundProgress(project, KotlinGradleJavaBundle.message("progress.title.updating.gradle.scripts.with.sources")) {
            project.scriptConfigurationsSourceOfType<GradleScriptConfigurationsSource>()?.updateModules()
          }
        }

        fileEditor.file?.let { FileEditorManager.getInstance(project).closeFile(it) }
      }

    }

  private fun VirtualFile.isScriptDependency(project: Project): Boolean {
    val workspaceModel = WorkspaceModel.getInstance(project)
    val index = workspaceModel.currentSnapshot.getVirtualFileUrlIndex()
    val fileUrlManager = workspaceModel.getVirtualFileUrlManager()

    var currentFile: VirtualFile? = this
    while (currentFile != null) {
      val entities = index.findEntitiesByUrl(fileUrlManager.getOrCreateFromUrl(currentFile.url))
      if (entities.none()) {
        currentFile = currentFile.parent
        continue
      }

      return entities.firstOrNull { it.entitySource is KotlinGradleScriptModuleEntitySource } != null
    }

    return false
  }

  @Service(Service.Level.PROJECT)
  private class CoroutineScopeService(val coroutineScope: CoroutineScope) {
    companion object {
      fun getCoroutineScope(project: Project): CoroutineScope = project.service<CoroutineScopeService>().coroutineScope
    }
  }
}
