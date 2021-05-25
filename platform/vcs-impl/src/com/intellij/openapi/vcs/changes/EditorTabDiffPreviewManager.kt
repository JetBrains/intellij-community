// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.editor.ChainDiffVirtualFile
import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.diff.editor.DiffEditorTabFilesManager.Companion.SHOW_DIFF_IN_EDITOR_SETTING
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerListener

class EditorTabDiffPreviewManager(private val project: Project) : DiffEditorTabFilesManager {

  fun isEditorDiffPreviewAvailable() = isEditorDiffAvailable()

  fun subscribeToPreviewVisibilityChange(disposable: Disposable, onVisibilityChange: Runnable) {
    ApplicationManager.getApplication().messageBus.connect(disposable)
      .subscribe(AdvancedSettingsChangeListener.TOPIC,
                 object : AdvancedSettingsChangeListener {
                   override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
                     if (id == SHOW_DIFF_IN_EDITOR_SETTING && oldValue != newValue) {
                       onVisibilityChange.run()
                     }
                   }
                 })
    project.messageBus.connect(disposable)
      .subscribe(ChangesViewContentManagerListener.TOPIC,
                 object : ChangesViewContentManagerListener {
                   override fun toolWindowMappingChanged() {
                     onVisibilityChange.run()
                   }
                 })
  }

  override fun showDiffFile(diffFile: ChainDiffVirtualFile, focusEditor: Boolean): Array<out FileEditor> {
    return VcsEditorTabFilesManager.getInstance().openFile(project, diffFile, focusEditor)
  }

  fun showDiffPreview(diffPreview: DiffPreview) {
    diffPreview.setPreviewVisible(true, true)
  }

  companion object {
    @JvmField
    val EDITOR_TAB_DIFF_PREVIEW = DataKey.create<DiffPreview>("EditorTabDiffPreview")

    @JvmStatic
    fun getInstance(project: Project): EditorTabDiffPreviewManager =
      project.service<DiffEditorTabFilesManager>() as EditorTabDiffPreviewManager
  }
}
