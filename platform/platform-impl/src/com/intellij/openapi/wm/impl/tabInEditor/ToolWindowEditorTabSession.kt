// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.ui.content.Content
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import javax.swing.JComponent

/**
 * Holds the project-specific runtime state of a [ToolWindowEditorTabFile].
 *
 * A [ToolWindowEditorTabFile] only identifies an editor tab and carries no project-specific or UI state.
 *
 * A [ToolWindowEditorTabSession] is created and attached to a file by [ToolWindowEditorTabManager]:
 * - when the tab is moved from a tool window to the editor by [ToolWindowEditorTabTransferController.moveContentToEditor];
 * - when persisted tab content is restored by [ToolWindowEditorTabFileEditor.setState].
 *
 * A file may exist without a session. This happens during restoration, where the application-level VFS has to
 * resolve a persistent file before the project and the corresponding tool window content are available.
 *
 * @param project the project in which the editor tab is open
 * @param file the virtual file identifying the editor tab
 * @param content the tool window content represented by the editor tab
 * @param component the root component displayed by the file editor for [content]
 * @param preferredFocusedComponent the component preferred as the initial focus target when the editor tab gains focus
 * @param presentationFlow the presentation of the tab and its subsequent updates
 * @param coroutineScope the scope owning asynchronous work associated with this session; it is cancelled
 * when the session is closed
 */
internal class ToolWindowEditorTabSession(
  val project: Project,
  val file: ToolWindowEditorTabFile,
  val content: Content,
  val component: JComponent,
  val preferredFocusedComponent: JComponent,
  presentationFlow: Flow<ToolWindowEditorTabPresentation>,
  private val coroutineScope: CoroutineScope,
) {
  @Volatile
  var presentation: ToolWindowEditorTabPresentation? = null
    private set

  init {
    presentationFlow
      .onEach { newPresentation ->
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          if (!file.isValid || project.isDisposed) {
            return@withContext
          }

          if (applyPresentation(newPresentation)) {
            FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(file)
          }
        }
      }
      .launchIn(coroutineScope)
  }

  @RequiresEdt
  private fun applyPresentation(newPresentation: ToolWindowEditorTabPresentation): Boolean {
    if (presentation == newPresentation) {
      return false
    }

    presentation = newPresentation
    file.updatePresentableName(newPresentation.title)
    return true
  }

  fun close(releaseContent: Boolean) {
    coroutineScope.cancel()
    if (releaseContent) {
      content.release()
    }
  }
}
