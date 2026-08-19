// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolderBase
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

internal class ToolWindowEditorTabFileEditor(
  private val project: Project,
  private val file: ToolWindowEditorTabFile,
) : UserDataHolderBase(), FileEditor {
  // Used while the file has no attached session and therefore no actual editor component is available.
  private val placeholder: JComponent by lazy(LazyThreadSafetyMode.NONE) { JPanel() }

  private val tabManager: ToolWindowEditorTabManager
    get() = ToolWindowEditorTabManager.getInstance(project)

  /**
   * Returns the runtime session associated with [file], or `null` while no content is attached.
   *
   * A session is available immediately when the file is created for content moved from a tool window
   * to the editor. For a persisted editor tab, the file may initially exist without a session; the
   * session is created later when [setState] restores and attaches the persisted tool window content.
   */
  private val session: ToolWindowEditorTabSession?
    get() = tabManager.getSession(file)

  override fun getComponent(): JComponent = session?.component ?: placeholder

  override fun getPreferredFocusedComponent(): JComponent = session?.preferredFocusedComponent ?: placeholder

  override fun getName(): @NlsSafe String = tabManager.getTabTitle(file)

  /**
   * Returns the persistent state of the tool window content represented by this editor.
   *
   * A state can be produced only when the editor has an attached [ToolWindowEditorTabSession],
   * the underlying [ToolWindowEditorTabFile] is persistent, and the corresponding
   * [ToolWindowEditorTabPersistenceProvider] can serialize the attached content.
   *
   * Returns [FileEditorState.INSTANCE] when there is no state to persist.
   */
  override fun getState(level: FileEditorStateLevel): FileEditorState {
    val content = session?.content ?: return FileEditorState.INSTANCE
    if (file.persistentPath == null) return FileEditorState.INSTANCE

    val provider =
      ToolWindowEditorTabPersistenceProviderUtil.getProvider(file.toolWindowId)
      ?: return FileEditorState.INSTANCE

    if (!provider.canSerialize(content)) return FileEditorState.INSTANCE

    return ToolWindowEditorTabState(provider.serialize(content))
  }

  /**
   * Restores the tool window content represented by this editor from the persisted [state].
   *
   * During editor restoration, a [ToolWindowEditorTabFile] may already exist without an attached
   * [ToolWindowEditorTabSession]. In that case, a [ToolWindowEditorTabState] is used to deserialize
   * the tool window content and attach it to the file, creating the corresponding session.
   *
   * If a session is already attached, the state is ignored. If the persisted content cannot be
   * restored, the editor tab is closed and the file is removed from further restoration.
   */
  override fun setState(state: FileEditorState) {
    val tabState = state as? ToolWindowEditorTabState ?: return

    if (session != null) {
      return
    }

    if (!tabManager.restoreEditorTabFileContent(file, tabState)) {
      // The persisted content cannot be restored, so remove the corresponding editor tab.
      // Content is released by restoreEditorTabFileContent if it was created.
      tabManager.closeEditorTabFile(file, releaseContent = false)
    }
  }

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = file.isValid

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

  override fun getFile() = file

  override fun dispose() {
    tabManager.closeEditorTabFile(file, releaseContent = true)
    FileEditorManager.getInstance(project).closeFile(this.file)
  }
}
