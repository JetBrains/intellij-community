// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui


import com.intellij.idea.AppMode
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import com.intellij.platform.debugger.impl.rpc.XBreakpointId
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy
import com.intellij.platform.rpc.topics.sendToClient
import com.intellij.xdebugger.SplitDebuggerMode
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.impl.breakpoints.SHOW_BREAKPOINT_DIALOG_REMOTE_TOPIC
import com.intellij.xdebugger.impl.breakpoints.ShowBreakpointDialogRequest
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting


@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class BreakpointsDialogFactory(private val project: Project, private val scope: CoroutineScope) {
  private var balloonToHide: Balloon? = null
  private var breakpointFromBalloon: Any? = null

  @VisibleForTesting
  var showingDialog: BreakpointsDialog? = null

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

  fun popupRequested(breakpoint: XBreakpointProxy?): Boolean {
    if (balloonToHide != null && !balloonToHide!!.isDisposed()) {
      return true
    }
    return selectInDialogShowing(breakpoint?.id)
  }

  fun showDialog(initialBreakpoint: XBreakpoint<*>?) {
    val initialBreakpointId = (initialBreakpoint as? XBreakpointBase<*, *, *>)?.breakpointId
    showDialog(initialBreakpointId)
  }

  fun showDialog(initialBreakpointId: XBreakpointId?) {
    if (SplitDebuggerMode.isSplitDebugger() && AppMode.isRemoteDevHost()) {
      hideBalloon()
      SHOW_BREAKPOINT_DIALOG_REMOTE_TOPIC.sendToClient(project, ShowBreakpointDialogRequest(initialBreakpointId))
      return
    }
    showDialogImpl(initialBreakpointId)
  }

  @ApiStatus.Internal
  fun showDialogImpl(initialBreakpoint: XBreakpointId?) {
    if (initialBreakpoint == null) {
      showDialogImplImmediately(initialBreakpoint)
      return
    }

    scope.launch {
      XDebugManagerProxy.getInstance()
        .getBreakpointManagerProxy(project)
        .awaitBreakpointCreation(initialBreakpoint)
      withContext(Dispatchers.EDT) {
        showDialogImplImmediately(initialBreakpoint)
      }
    }
  }

  private fun showDialogImplImmediately(initialBreakpoint: XBreakpointId?) {
    if (selectInDialogShowing(initialBreakpoint)) return

    val breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)
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

  private fun hideBalloon() {
    if (balloonToHide != null) {
      if (!balloonToHide!!.isDisposed()) {
        balloonToHide!!.hide()
      }
      balloonToHide = null
    }
  }

  private fun selectInDialogShowing(initialBreakpoint: XBreakpointId?): Boolean {
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
