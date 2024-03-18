// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.diff.editor.DiffEditorTabFilesManager.Companion.isDiffInEditor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerListener
import com.intellij.openapi.vfs.VirtualFile

class EditorTabDiffPreviewManager(private val project: Project) : DiffEditorTabFilesManager {

  fun subscribeToPreviewVisibilityChange(disposable: Disposable, onVisibilityChange: Runnable) {
    project.messageBus.connect(disposable)
      .subscribe(ChangesViewContentManagerListener.TOPIC,
                 object : ChangesViewContentManagerListener {
                   override fun toolWindowMappingChanged() {
                     onVisibilityChange.run()
                   }
                 })
  }

  override fun showDiffFile(diffFile: VirtualFile, focusEditor: Boolean): Array<out FileEditor> {
    return VcsEditorTabFilesManager.getInstance().openFile(project, diffFile, focusEditor, !isDiffInEditor, false)
  }

  companion object {
    @JvmField
    val EDITOR_TAB_DIFF_PREVIEW = DataKey.create<DiffPreview>("EditorTabDiffPreview")

    @JvmStatic
    fun getInstance(project: Project): EditorTabDiffPreviewManager =
      DiffEditorTabFilesManager.getInstance(project) as EditorTabDiffPreviewManager
  }
}
