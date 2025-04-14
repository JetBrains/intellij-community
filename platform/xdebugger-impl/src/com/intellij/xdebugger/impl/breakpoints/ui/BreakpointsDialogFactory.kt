// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy.Companion.useFeProxy
import com.intellij.xdebugger.impl.rpc.XBreakpointId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus


@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class BreakpointsDialogFactory(private val project: Project) {
  private var balloonToHide: Balloon? = null
  private var breakpointFromBalloon: Any? = null
  private var showingDialog: BreakpointsDialog? = null

  private val showDialogEvents = MutableSharedFlow<Any?>(extraBufferCapacity = 1)

  // should be used only by backend RPC, so frontend will handle backend requests
  fun subscribeToShowDialogEvents(cs: CoroutineScope, onShowDialogRequest: suspend (breakpoint: Any?) -> Unit) {
    cs.launch(Dispatchers.EDT) {
      showDialogEvents.collectLatest {
        onShowDialogRequest(it)
      }
    }
  }

  fun setBalloonToHide(balloon: Balloon, breakpoint: Any?) {
    balloonToHide = balloon
    breakpointFromBalloon = breakpoint
    Disposer.register(balloon) {
      if (balloonToHide === balloon) {
        balloonToHide = null
        breakpointFromBalloon = null
      }
    }
  }

  fun popupRequested(breakpoint: Any?): Boolean {
    if (balloonToHide != null && !balloonToHide!!.isDisposed()) {
      return true
    }
    return selectInDialogShowing(breakpoint)
  }

  fun showDialog(initialBreakpoint: Any?) {
    if (useFeProxy()) {
      hideBalloon()
      showDialogEvents.tryEmit(initialBreakpoint)
      return
    }
    showDialogImpl(initialBreakpoint)
  }

  @ApiStatus.Internal
  fun showDialogImpl(initialBreakpoint: Any?) {
    if (selectInDialogShowing(initialBreakpoint)) return

    val breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)
    val initialBreakpoint = convertToInitialBreakpoint(initialBreakpoint ?: breakpointFromBalloon)
    val dialog = object : BreakpointsDialog(project, initialBreakpoint, breakpointManager) {
      override fun dispose() {
        breakpointFromBalloon = null
        showingDialog = null

        super.dispose()
      }
    }

    hideBalloon()
    showingDialog = dialog

    dialog.show()
  }

  private fun convertToInitialBreakpoint(initialBreakpoint: Any?): BreakpointsDialogInitialBreakpoint? {
    if (initialBreakpoint == null) {
      return null
    }
    return when (initialBreakpoint) {
      is XBreakpointId -> BreakpointsDialogInitialBreakpoint.BreakpointId(initialBreakpoint)
      else -> BreakpointsDialogInitialBreakpoint.GenericBreakpoint(initialBreakpoint)
    }
  }

  private fun hideBalloon() {
    if (balloonToHide != null) {
      if (!balloonToHide!!.isDisposed()) {
        balloonToHide!!.hide()
      }
      balloonToHide = null
    }
  }

  private fun selectInDialogShowing(initialBreakpoint: Any?): Boolean {
    if (showingDialog != null) {
      val window = showingDialog!!.window
      if (window != null && window.isDisplayable) { // workaround for IDEA-197804
        showingDialog!!.selectBreakpoint(initialBreakpoint, true)
        showingDialog!!.toFront()
        return true
      }
    }
    return false
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): BreakpointsDialogFactory {
      return project.service<BreakpointsDialogFactory>()
    }
  }
}
