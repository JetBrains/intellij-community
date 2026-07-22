// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager.OptionallyIncluded
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.content.Content
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import javax.swing.JComponent

/**
 * Represents a virtual file for displaying tool window content in an editor tab.
 *
 * @param editorTitle The initial title of the editor tab.
 * @param toolWindowId The ID of the associated tool window.
 * @param component The UI component displayed in the editor tab.
 * @param preferredFocusedComponent The component that should receive focus when the editor tab is selected.
 * @param content The tool window content represented by this editor tab.
 * @param persistInEditorHistory Whether the editor tab should be persisted in the editor history.
 * @param tabIcon The initial icon of the editor tab.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
class ToolWindowEditorTabFile(
  editorTitle: String,
  val toolWindowId: String,
  val component: JComponent,
  internal val preferredFocusedComponent: JComponent,
  internal val content: Content,
  internal val project: Project,
  tabIcon: Icon? = null,
) : LightVirtualFile(editorTitle, ToolWindowEditorTabFileType, ""), OptionallyIncluded {

  internal var tabIcon: Icon? = tabIcon
    private set

  init {
    putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
  }

  override fun isIncludedInEditorHistory(project: Project): Boolean = true

  // TODO: Enable persistence when tool window editor tabs can be restored between IDE sessions.
  override fun isPersistedInEditorHistory(): Boolean = false

  override fun isWritable(): Boolean = true

  internal fun onEditorClosed() {
    if (getUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN) != true) {
      invalidateEditorTabFile()
    }
  }

  internal fun invalidateEditorTabFile() {
    isValid = false // mark invalid, so file does not appear in the recent files
  }
}
