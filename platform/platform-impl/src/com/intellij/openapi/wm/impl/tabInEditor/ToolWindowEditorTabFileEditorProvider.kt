// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.ide.ui.icons.rpcIdOrNull
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.registerCreatedFileEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.fileEditor.impl.FrontendOwnedBackendMirrorFileDescriptor
import com.intellij.openapi.fileEditor.impl.FrontendOwnedFileEditorSplitBinder
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jdom.Element

private const val TOOL_WINDOW_EDITOR_TAB_EDITOR_TYPE_ID: String = "ToolWindowEditorTabFileEditor"

internal class ToolWindowEditorTabFileEditorProvider : AsyncFileEditorProvider {
  override fun accept(project: Project, file: VirtualFile): Boolean = file is ToolWindowEditorTabFile

  override fun acceptRequiresReadAction(): Boolean = false

  override suspend fun createFileEditor(
    project: Project,
    file: VirtualFile,
    document: Document?,
    editorCoroutineScope: CoroutineScope,
  ): FileEditor {
    val tabFile = file as ToolWindowEditorTabFile
    val editor = ToolWindowEditorTabFileEditor(project, tabFile)
    registerCreatedFileEditor(editor)
    val mirrorFile = tabFile.toFrontendOwnedBackendMirrorFileDescriptorOrNull(project)
    FrontendOwnedFileEditorSplitBinder.tryBindExistingFrontendEditorToBackendMirror(
      project = project,
      file = tabFile,
      provider = this,
      editor = editor,
      mirrorFile = mirrorFile,
    )

    return editor
  }

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

private const val TOOL_WINDOW_EDITOR_TAB_BACKEND_MIRROR_NAMESPACE: String = "platform.toolWindowEditorTab"

/**
 * Creates a descriptor for the backend mirror of this tool window editor tab.
 *
 * [ToolWindowEditorTabFile] exists only on the frontend and therefore cannot be resolved on the backend.
 * The mirror provides the backend with enough information to represent the tab in backend-owned features,
 * such as Recent Files.
 */
private fun ToolWindowEditorTabFile.toFrontendOwnedBackendMirrorFileDescriptorOrNull(
  project: Project,
): FrontendOwnedBackendMirrorFileDescriptor {
  val tabManager = ToolWindowEditorTabManager.getInstance(project)
  return FrontendOwnedBackendMirrorFileDescriptor(
    namespace = TOOL_WINDOW_EDITOR_TAB_BACKEND_MIRROR_NAMESPACE,
    id = stableId,
    presentableName = tabManager.getTabTitle(this),
    iconId = tabManager.getTabPresentation(this)?.icon?.rpcIdOrNull(),
  )
}

/**
 * Binds [editorWithProvider] to the backend mirror of [file] and synchronizes the tab presentation
 * with the mirror.
 *
 * @return `true` if the frontend editor was successfully bound to the backend mirror; `false` otherwise
 */
internal suspend fun syncToolWindowEditorTabWithBackendMirror(
  project: Project,
  file: ToolWindowEditorTabFile,
  editorWithProvider: FileEditorWithProvider,
): Boolean {
  return FrontendOwnedFileEditorSplitBinder.tryBindExistingFrontendEditorToBackendMirror(
    project = project,
    file = file,
    provider = editorWithProvider.provider,
    editor = editorWithProvider.fileEditor,
    mirrorFile = file.toFrontendOwnedBackendMirrorFileDescriptorOrNull(project),
  )
}

/**
 * Binds the currently selected editor for [file] to its backend mirror and synchronizes the tab
 * presentation with the mirror.
 *
 * @return `true` if the selected frontend editor was successfully bound to the backend mirror;
 * `false` if [file] has no selected editor, for example while its editor composite is still loading,
 * or if the editor could not be bound.
 */
internal suspend fun syncToolWindowEditorTabWithBackendMirror(project: Project, file: ToolWindowEditorTabFile): Boolean {
  val editorWithProvider = withContext(Dispatchers.EDT) {
    FileEditorManagerEx.getInstanceEx(project).getSelectedEditorWithProvider(file)
  } ?: return false

  return syncToolWindowEditorTabWithBackendMirror(project, file, editorWithProvider)
}
