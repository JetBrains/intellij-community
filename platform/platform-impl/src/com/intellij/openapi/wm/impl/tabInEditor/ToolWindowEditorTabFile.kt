// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager.OptionallyIncluded
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.content.Content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import javax.swing.JComponent

/**
 * Represents a virtual file for displaying tool window content in an editor tab.
 *
 * @param presentationFlow The state flow that provides the initial presentation and subsequent presentation updates.
 * @param toolWindowId The ID of the associated tool window.
 * @param component The UI component displayed in the editor tab.
 * @param preferredFocusedComponent The component that should receive focus when the editor tab is selected.
 * @param content The tool window content represented by this editor tab.
 * @param project The project associated with the editor tab.
 * @param parentCoroutineScope The parent scope used to create a child scope for collecting presentation updates.
 */
@ApiStatus.Experimental
@ApiStatus.Internal
class ToolWindowEditorTabFile private constructor(
  initialPresentation: ToolWindowEditorTabPresentation,
  presentationFlow: StateFlow<ToolWindowEditorTabPresentation>,
  val toolWindowId: String,
  val component: JComponent,
  internal val preferredFocusedComponent: JComponent,
  internal val content: Content,
  internal val project: Project,
  parentCoroutineScope: CoroutineScope,
) : LightVirtualFile(
  initialPresentation.title,
  ToolWindowEditorTabFileType,
  "",
), OptionallyIncluded {

  internal constructor(
    presentationFlow: StateFlow<ToolWindowEditorTabPresentation>,
    toolWindowId: String,
    component: JComponent,
    preferredFocusedComponent: JComponent,
    content: Content,
    project: Project,
    parentCoroutineScope: CoroutineScope,
  ) : this(
    initialPresentation = presentationFlow.value,
    presentationFlow = presentationFlow,
    toolWindowId = toolWindowId,
    component = component,
    preferredFocusedComponent = preferredFocusedComponent,
    content = content,
    project = project,
    parentCoroutineScope = parentCoroutineScope,
  )

  private val coroutineScope = parentCoroutineScope.childScope(
    "ToolWindowEditorTabFile[$toolWindowId]",
  )

  internal var tabIcon: Icon? = initialPresentation.icon
    private set

  init {
    putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)

    presentationFlow
      .onEach { presentation ->
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          if (!isValid || project.isDisposed) {
            return@withContext
          }

          if (updatePresentation(presentation)) {
            FileEditorManagerEx.getInstanceEx(project)
              .updateFilePresentation(this@ToolWindowEditorTabFile)
          }
        }
      }
      .launchIn(coroutineScope)
  }

  private fun updatePresentation(
    presentation: ToolWindowEditorTabPresentation,
  ): Boolean {
    var changed = false

    if (name != presentation.title) {
      rename(null, presentation.title)
      changed = true
    }

    if (tabIcon != presentation.icon) {
      tabIcon = presentation.icon
      changed = true
    }

    return changed
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
    coroutineScope.cancel()
  }
}
