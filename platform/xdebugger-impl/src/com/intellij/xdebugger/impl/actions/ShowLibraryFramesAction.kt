// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.rpc.XDebuggerManagerApi
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal class ShowLibraryFramesAction : ToggleAction(), FrontendOtherwiseBackend {

  init {
    templatePresentation.apply {
      text = XDebuggerBundle.message("show.all.frames.tooltip")
      description = ""
      icon = AllIcons.General.Filter
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    with(e.presentation) {
      if (!isLibraryFrameFilterSupported(e, this)) {
        isVisible = false
        return
      }
      isVisible = true
      // Change the tooltip of a button in a toolbar and don't change anything for a context menu.
      if (e.isFromActionToolbar) {
        val shouldShow = !isSelected(e)
        text = XDebuggerBundle.message(if (shouldShow) "hide.library.frames.tooltip" else "show.all.frames.tooltip")
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return !XDebuggerSettingManagerImpl.getInstanceImpl().dataViewSettings.isShowLibraryStackFrames
  }

  override fun setSelected(e: AnActionEvent, enabled: Boolean) {
    e.project?.service<ShowLibraryFramesActionCoroutineScope>()?.toggle(!enabled)
  }

  companion object {
    // we should remember initial answer "isLibraryFrameFilterSupported" because on stop no debugger process, but UI is still shown
    // - we should avoid "jumping" (visible (start) - invisible (stop) - visible (start again))
    private const val IS_LIBRARY_FRAME_FILTER_SUPPORTED = "isLibraryFrameFilterSupported"

    private fun isLibraryFrameFilterSupported(e: AnActionEvent, presentation: Presentation): Boolean {
      var isSupported = presentation.getClientProperty(IS_LIBRARY_FRAME_FILTER_SUPPORTED)
      val sessionProxy = DebuggerUIUtil.getSessionProxy(e)
      if (isSupported == null) {
        if (sessionProxy == null) {
          // if sessionProxy is null and isSupported is null - just return, it means that action created initially not in the xdebugger tab
          presentation.setVisible(false)
          return false
        }

        isSupported = sessionProxy.isLibraryFrameFilterSupported
        presentation.putClientProperty(IS_LIBRARY_FRAME_FILTER_SUPPORTED, isSupported)
      }

      return isSupported == true
    }
  }
}

@Suppress("OPT_IN_USAGE")
@Service(Service.Level.PROJECT)
internal class ShowLibraryFramesActionCoroutineScope(private val project: Project, cs: CoroutineScope) {
  private val toggleFlow = MutableStateFlow(false)

  init {
    cs.launch {
      toggleFlow.debounce(30.milliseconds).collectLatest { show ->
        // update on frontend optimistically
        XDebuggerSettingManagerImpl.getInstanceImpl().dataViewSettings.isShowLibraryStackFrames = show

        XDebuggerManagerApi.getInstance().showLibraryFrames(show)
        XDebuggerUtilImpl.rebuildAllSessionsViews(project)
      }
    }
  }

  fun toggle(show: Boolean) {
    toggleFlow.tryEmit(show)
  }

  companion object {
    fun getInstance(project: Project): ShowLibraryFramesActionCoroutineScope = project.service()
  }
}
