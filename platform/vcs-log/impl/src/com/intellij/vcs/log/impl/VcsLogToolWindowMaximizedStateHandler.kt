// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EdtImmediate
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.VcsLogBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * REALLY temporary and experimental thing to support a kind of "Focus mode" for the VCS toolwindow
 * It would be much better to install the action in [com.intellij.openapi.vcs.changes.ui.ChangeViewToolWindowFactory]
 */
internal class VcsLogToolWindowMaximizedStateHandler : ProjectActivity {
  override suspend fun execute(project: Project) {
    // this activity never ends and some tests can't handle that
    if (ApplicationManager.getApplication().isUnitTestMode) return

    val logSettings = serviceAsync<VcsLogApplicationSettings>()
    val savedSettings = serviceAsync<VcsLogToolWindowMaximizedStateComponent>()

    @RequiresEdt
    fun setMaximizedSettings() {
      savedSettings.diffPreviewSaved = logSettings[CommonUiProperties.SHOW_DIFF_PREVIEW]
      logSettings[CommonUiProperties.SHOW_DIFF_PREVIEW] = true
      savedSettings.diffPreviewVerticalSaved = logSettings[MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT]
      logSettings[MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT] = true
    }

    @RequiresEdt
    fun restoreSavedSettings() {
      val diffPreviewSaved = savedSettings.diffPreviewSaved
      if (diffPreviewSaved != null) {
        logSettings[CommonUiProperties.SHOW_DIFF_PREVIEW] = diffPreviewSaved
        savedSettings.diffPreviewSaved = null
      }
      val diffPreviewVerticalSaved = savedSettings.diffPreviewVerticalSaved
      if (diffPreviewVerticalSaved != null) {
        logSettings[MainVcsLogUiProperties.DIFF_PREVIEW_VERTICAL_SPLIT] = diffPreviewVerticalSaved
        savedSettings.diffPreviewVerticalSaved = null
      }
    }

    withContext(Dispatchers.EdtImmediate) {
      // maximized state is not persisted now, so it's overkill, but I already wrote it and might be useful later
      // when is it persisted, the state should be restored here first
      project.vcsToolWindowFlow().collectLatest { toolWindow ->
        if (toolWindow != null) {
          coroutineScope {
            launch {
              project.toolWindowMaximizedFlow(toolWindow).collect { maximized ->
                if (maximized) {
                  setMaximizedSettings()
                }
                else {
                  restoreSavedSettings()
                }
              }
            }
            showMaximizeActionAsTextInToolbar(toolWindow)
          }
        }
      }
    }
  }

  private suspend fun showMaximizeActionAsTextInToolbar(toolWindow: ToolWindow): Nothing {
    serviceAsync<ActionManager>()
      .getAction("MaximizeToolWindow")
      ?.let(::wrapMaximizeAction)
      ?.let { toolWindow.setTitleActions(listOf(it)) }

    try {
      awaitCancellation()
    }
    finally {
      toolWindow.setTitleActions(emptyList())
    }
  }

  private fun wrapMaximizeAction(action: AnAction): AnAction =
    object : AnActionWrapper(action) {
      override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
        val maximized = Toggleable.isSelected(e.presentation)
        e.presentation.text =
          if (!maximized) VcsLogBundle.message("vcs.log.toolwindow.maximize")
          else VcsLogBundle.message("vcs.log.toolwindow.restore.size")
        e.presentation.icon =
          if (!maximized) AllIcons.General.FitContent
          else AllIcons.General.CollapseComponent
      }
    }
}

@Service(Service.Level.APP)
@State(name = "Vcs.Log.App.Settings.Maximized", storages = [Storage("vcs.xml")], category = SettingsCategory.TOOLS)
internal class VcsLogToolWindowMaximizedStateComponent : PersistentStateComponent<VcsLogToolWindowMaximizedStateComponent.State?> {
  private var state = State()

  var diffPreviewSaved: Boolean?
    get() = state.diffPreviewSaved
    set(value) {
      state.diffPreviewSaved = value
    }

  var diffPreviewVerticalSaved: Boolean?
    get() = state.diffPreviewVerticalSaved
    set(value) {
      state.diffPreviewVerticalSaved = value
    }

  override fun getState(): State = state

  override fun loadState(state: State) {
    this.state = state
  }

  internal class State(
    var diffPreviewSaved: Boolean? = null,
    var diffPreviewVerticalSaved: Boolean? = null,
  )
}

private fun Project.vcsToolWindowFlow(): Flow<ToolWindow?> = callbackFlow {
  messageBus.connect(this).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
    override fun toolWindowsRegistered(ids: List<String?>, toolWindowManager: ToolWindowManager) {
      if (!ids.contains(ToolWindowId.VCS)) return
      toolWindowManager.getToolWindow(ToolWindowId.VCS)?.let(::trySend)
    }

    override fun toolWindowUnregistered(id: String, toolWindow: ToolWindow) {
      if (id == ToolWindowId.VCS) {
        trySend(null)
      }
    }
  })
  serviceAsync<ToolWindowManager>().getToolWindow(ToolWindowId.VCS)?.let {
    send(it)
  }
  awaitClose()
}.distinctUntilChanged().flowOn(Dispatchers.UI)

private fun Project.toolWindowMaximizedFlow(targetToolWindow: ToolWindow): Flow<Boolean> = callbackFlow {
  messageBus.connect(this).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
    override fun stateChanged(
      toolWindowManager: ToolWindowManager,
      toolWindow: ToolWindow,
      changeType: ToolWindowManagerListener.ToolWindowManagerEventType,
    ) {
      if (changeType == ToolWindowManagerListener.ToolWindowManagerEventType.MovedOrResized && toolWindow == targetToolWindow) {
        trySend(toolWindowManager.isMaximized(toolWindow))
      }
    }
  })
  serviceAsync<ToolWindowManager>().isMaximized(targetToolWindow).let {
    send(it)
  }
  awaitClose()
}.distinctUntilChanged().flowOn(Dispatchers.UI)