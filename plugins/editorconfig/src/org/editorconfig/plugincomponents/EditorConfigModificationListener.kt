// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.plugincomponents

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.editorconfig.EditorConfigRegistry
import org.editorconfig.configmanagement.EditorSettingsManager
import org.editorconfig.core.ec4jwrappers.EditorConfigPermanentCache

internal class EditorConfigModificationListener : BulkFileListener {
  override fun after(events: List<VFileEvent>) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }
    for (event in events) {
      val file = event.file
      if (file == null || file.name != ".editorconfig") {
        continue
      }
      for (project in ProjectManager.getInstance().openProjects) {
        if (ProjectRootManager.getInstance(project).fileIndex.isInContent(file) ||
            !EditorConfigRegistry.shouldStopAtProjectRoot()) {
          project.service<EditorConfigPermanentCache>().clear()
          // TODO this is the part that might not be needed anymore
          ApplicationManager.getApplication().invokeLater {
            SettingsProviderComponent.getInstance(project).incModificationCount()
            for (editor in EditorFactory.getInstance().allEditors) {
              if (editor.isDisposed) continue
              EditorSettingsManager.applyEditorSettings(editor)
              (editor as EditorEx).reinitSettings()
            }
          }
        }
      }
    }
  }
}