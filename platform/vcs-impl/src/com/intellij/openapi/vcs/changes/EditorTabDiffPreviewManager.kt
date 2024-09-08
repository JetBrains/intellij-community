// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerListener

@Deprecated("Use DiffEditorTabFilesManager instead")
class EditorTabDiffPreviewManager(private val project: Project) {
  fun subscribeToPreviewVisibilityChange(disposable: Disposable, onVisibilityChange: Runnable) {
    project.messageBus.connect(disposable)
      .subscribe(ChangesViewContentManagerListener.TOPIC,
                 object : ChangesViewContentManagerListener {
                   override fun toolWindowMappingChanged() {
                     onVisibilityChange.run()
                   }
                 })
  }

  companion object {
    @JvmField
    val EDITOR_TAB_DIFF_PREVIEW = DataKey.create<DiffPreview>("EditorTabDiffPreview")
  }
}
