// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class BreakpointsDialogFactory(private val project: Project) {
  private var balloonToHide: Balloon? = null
  private var breakpointFromBalloon: Any? = null
  private var showingDialog: BreakpointsDialog? = null

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
    if (selectInDialogShowing(initialBreakpoint)) return

    val dialog = object : BreakpointsDialog(project, initialBreakpoint ?: breakpointFromBalloon) {
      override fun dispose() {
        breakpointFromBalloon = null
        showingDialog = null

        super.dispose()
      }
    }

    if (balloonToHide != null) {
      if (!balloonToHide!!.isDisposed()) {
        balloonToHide!!.hide()
      }
      balloonToHide = null
    }
    showingDialog = dialog

    dialog.show()
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
