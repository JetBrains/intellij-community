// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager.OptionallyIncluded
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.content.Content
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import javax.swing.JComponent

internal val SKIP_EDITOR_TAB_CLOSE_HANDLER: Key<Boolean?> = Key.create("tool.window.editor.tab.skip.close.handler")

/**
 * Represents a virtual file specific to a tool window tab.
 * This is a holder of tool window representative information.
 * The class needs to be open to support changes in the editor title by overriding the `LightVirtualFileBase.getName` method.
 *
 * Note: This API is marked as experimental and internal, and its usage or behavior might change in future versions.
 *
 * @param editorTitle The name of the title of the editor.
 * @param fileType The optional file type.
 * @param toolWindowId The ID of the tool window associated with this file.
 * @param component A UI component that was initially displayed in a tool window tab.
 * @param preferredFocusedComponent The component that should receive focus when the editor tab is selected.
 * @param persistInEditorHistory Whether this tab should be persisted in the editor history.
 * @param content The tool window content represented by this editor tab.
 * @param onEditorClosed A callback invoked when the editor tab is closed by the regular editor close flow.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
open class ToolWindowEditorTabFile(
  editorTitle: String,
  val toolWindowId: String,
  fileType: FileType?,
  val component: JComponent,
  private val preferredFocusedComponent: JComponent,
  private val persistInEditorHistory: Boolean = false,
  internal val content: Content? = null,
  private val onEditorClosed: ((ToolWindowEditorTabFile) -> Unit)? = null,
) : LightVirtualFile(editorTitle, fileType, ""), OptionallyIncluded {
  private val editorLifetime = Disposer.newDisposable("ToolWindowEditorTabFile: $editorTitle")

  init {
    putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
  }

  internal fun getPreferredFocusedComponent(): JComponent {
    return preferredFocusedComponent
  }

  override fun isIncludedInEditorHistory(project: Project): Boolean = true
  override fun isPersistedInEditorHistory(): Boolean = persistInEditorHistory

  final override fun isWritable(): Boolean = true

  open fun onEditorClosed() {
    if (getUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN) == true ||
        getUserData(SKIP_EDITOR_TAB_CLOSE_HANDLER) == true) {
      return
    }
    onEditorClosed?.invoke(this)
  }

  internal fun bindContentToEditorLifetime() {
    content?.let {
      Disposer.register(editorLifetime, it)
    }
  }

  internal fun releaseEditorLifetime() {
    Disposer.dispose(editorLifetime)
  }

  internal fun withSkippedCloseHandler(action: () -> Unit) {
    putUserData(SKIP_EDITOR_TAB_CLOSE_HANDLER, true)
    try {
      action()
    }
    finally {
      putUserData(SKIP_EDITOR_TAB_CLOSE_HANDLER, null)
    }
  }
}

internal fun ToolWindowEditorTabDescriptor.toFileType(): FileType {
  return ToolWindowEditorTabFileType(icon)
}

private class ToolWindowEditorTabFileType(
  private val presentationIcon: Icon?,
) : FakeFileType() {
  override fun getName(): String = "ToolWindowEditorTab"

  @NlsSafe
  override fun getDescription(): String = name

  override fun getIcon(): Icon? = presentationIcon

  override fun isMyFileType(file: VirtualFile): Boolean = file is ToolWindowEditorTabFile
}
