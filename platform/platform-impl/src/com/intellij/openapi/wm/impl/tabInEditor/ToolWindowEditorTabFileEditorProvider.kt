// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jdom.Element

private const val TOOL_WINDOW_EDITOR_TAB_EDITOR_TYPE_ID: String = "ToolWindowEditorTabFileEditor"

internal class ToolWindowEditorTabFileEditorProvider : FileEditorProvider, DumbAware {
  override fun accept(project: Project, file: VirtualFile): Boolean = file is ToolWindowEditorTabFile

  override fun acceptRequiresReadAction(): Boolean = false

  override fun createEditor(project: Project, file: VirtualFile): FileEditor =
    ToolWindowEditorTabFileEditor(project, file as ToolWindowEditorTabFile)

  override fun getEditorTypeId(): String = TOOL_WINDOW_EDITOR_TAB_EDITOR_TYPE_ID

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

  /**
   * Restores the serialized tool window content state from [sourceElement].
   *
   * A valid persisted state is expected to contain exactly one child element.
   *
   * @return the restored [ToolWindowEditorTabState], or [FileEditorState.INSTANCE] if the persisted
   * state does not contain exactly one content-state element
   */
  override fun readState(
    sourceElement: Element,
    project: Project,
    file: Lazy<VirtualFile?>,
  ): FileEditorState {
    val contentState = sourceElement.children.singleOrNull()?.clone() ?: return FileEditorState.INSTANCE
    return ToolWindowEditorTabState(contentState)
  }

  /**
   * Writes the serialized tool window content state directly into [targetElement].
   *
   * The state element is cloned before being attached because a JDOM element can have only one parent.
   * States of other types are ignored.
   */
  override fun writeState(
    state: FileEditorState,
    project: Project,
    targetElement: Element,
  ) {
    val tabState = state as? ToolWindowEditorTabState ?: return
    targetElement.addContent(tabState.contentState.clone())
  }
}

/**
 * Editor state containing the serialized state of tool window content.
 *
 * [contentState] is produced by a [ToolWindowEditorTabPersistenceProvider] and later passed back to
 * that provider when the corresponding tool window editor tab is restored.
 *
 * @param contentState the provider-specific serialized state of the tool window content
 */
internal class ToolWindowEditorTabState(val contentState: Element) : FileEditorState {
  override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel): Boolean = false
}
