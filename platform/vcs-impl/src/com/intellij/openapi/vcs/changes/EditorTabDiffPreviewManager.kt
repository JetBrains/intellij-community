// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager

@Service(Service.Level.PROJECT)
class EditorTabDiffPreviewManager(private val project: Project) {

  fun isEditorDiffPreviewAvailable() =
    ChangesViewContentManager.isCommitToolWindowShown(project) || Registry.`is`("show.diff.preview.as.editor.tab")

  fun showDiffPreview(diffPreview: DiffPreview) {
    diffPreview.setPreviewVisible(true, true)
  }

  companion object {
    @JvmField
    val EDITOR_TAB_DIFF_PREVIEW = DataKey.create<DiffPreview>("EditorTabDiffPreview")

    @JvmStatic
    fun getInstance(project: Project): EditorTabDiffPreviewManager = project.service()
  }
}
