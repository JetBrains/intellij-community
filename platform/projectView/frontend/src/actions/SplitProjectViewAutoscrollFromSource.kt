// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.actions

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.actions.EditorChoice
import com.intellij.platform.projectView.actions.ProjectViewActionSupport
import com.intellij.platform.projectView.actions.SelectInSplitProjectViewImpl
import com.intellij.platform.projectView.frontend.window.ProjectViewToolWindowServiceImpl
import com.intellij.platform.projectView.settings.ProjectViewPaneOptionDTO
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsStateDTO
import com.intellij.util.asDisposable
import com.intellij.util.ui.update.DebouncedUpdates
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.awt.event.FocusEvent
import kotlin.time.Duration.Companion.milliseconds

@Service(Service.Level.PROJECT)
internal class SplitProjectViewAutoscrollFromSource(
  private val project: Project,
) {
  companion object {
    fun getInstance(project: Project): SplitProjectViewAutoscrollFromSource = project.service()
  }
  
  suspend fun manage() {
    coroutineScope {
      val optionService = ProjectViewActionSupport.getInstance(project)
      val selectionUpdates = DebouncedUpdates.forScope<Boolean>(
        scope = this,
        name = "Always select opened file debouncing",
        delay = 100.milliseconds,
      ).restartTimerOnAdd(true).runLatest { autoscroll ->
        if (!autoscroll) { // a request to cancel previously scheduled requests
          return@runLatest
        }
        if (optionService.getActionState()?.isAutoscrollFromSourceEnabled != true) {
          return@runLatest
        }
        SelectInSplitProjectViewImpl.getInstance(project).selectOpenedFile(EditorChoice.ALL_SELECTED, invokedManually = false)
      }

      fun autoscroll() {
        selectionUpdates.queue(true)
      }

      fun cancelAutoscroll() {
        selectionUpdates.queue(false)
      }

      launch(CoroutineName("Always select opened file on/off")) {
        optionService.getActionStateFlow().map { 
          it?.isAutoscrollFromSourceEnabled
        }.distinctUntilChanged().collectLatest { 
          autoscroll()
        }
      }
      launch(CoroutineName("Selected editor")) {
        FileEditorManagerEx.getInstanceEx(project).getSelectedEditorFlow().collectLatest {
          autoscroll()
        }
      }
      launch(CoroutineName("Selected pane")) {
        ProjectViewToolWindowServiceImpl.getInstance(project).currentPaneFlow.collectLatest { 
          autoscroll()
        }
      }
      launch(CoroutineName("Editor focus")) {
        (EditorFactory.getInstance().eventMulticaster as? EditorEventMulticasterEx?)?.addFocusChangeListener(object : FocusChangeListener {
          override fun focusGained(editor: Editor, event: FocusEvent) {
            autoscroll()
          }

          override fun focusLost(editor: Editor, event: FocusEvent) {
            cancelAutoscroll()
          }
        }, asDisposable())
        awaitCancellation()
      }
    }
  }
}

private val ProjectViewPaneSettingsStateDTO.isAutoscrollFromSourceEnabled: Boolean
  get() = optionStates[ProjectViewPaneOptionDTO.AUTOSCROLL_FROM_SOURCE]?.isSelected == true
