// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager.OptionallyIncluded
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.content.Content
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import javax.swing.JComponent

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
 */
@ApiStatus.Experimental
@ApiStatus.Internal
open class ToolWindowEditorTabFile(
  editorTitle: String,
  val toolWindowId: String,
  fileType: FileType? = ToolWindowEditorTabFileType, // todo: keep one file type ToolWindowEditorTabFileType for all ToolWindowEditorTabFile
  val component: JComponent,
  private val preferredFocusedComponent: JComponent,
  private val persistInEditorHistory: Boolean = false,
  internal val content: Content? = null,
  internal var tabIcon: Icon? = null,
) : LightVirtualFile(editorTitle, fileType, ""), OptionallyIncluded {

  // Content normally belongs to its ContentManager's disposable tree.
  // Once it is moved into an editor tab, re-parent it to the editorLifetime instead,
  // because the original manager may be disposed.
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
    if (getUserData(FileEditorManagerKeys.CLOSING_TO_REOPEN) != true) {
      invalidateEditorTabFile()
    }
  }

  internal fun bindContentToEditorLifetime() {
    content?.let {
      Disposer.register(editorLifetime, it)
    }
  }

  internal fun invalidateEditorTabFile() {
    releaseEditorLifetime()
    isValid = false // mark invalid, so file does not appear in the recent files
  }

  internal fun updatePresentation(descriptor: ToolWindowEditorTabDescriptor) {
    if (name != descriptor.title) {
      rename(null, descriptor.title)
    }
    tabIcon = descriptor.icon
  }

  private fun releaseEditorLifetime() {
    Disposer.dispose(editorLifetime)
  }
}
