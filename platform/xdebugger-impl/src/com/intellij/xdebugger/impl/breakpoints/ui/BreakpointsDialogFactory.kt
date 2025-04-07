// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer

class BreakpointsDialogFactory(private val myProject: Project) {
  private var myBalloonToHide: Balloon? = null
  private var myBreakpoint: Any? = null
  private var myDialogShowing: BreakpointsDialog? = null


  fun setBalloonToHide(balloonToHide: Balloon?, breakpoint: Any?) {
    myBalloonToHide = balloonToHide
    myBreakpoint = breakpoint
    Disposer.register(myBalloonToHide!!, object : Disposable {
      override fun dispose() {
        if (myBalloonToHide === balloonToHide) {
          myBalloonToHide = null
          myBreakpoint = null
        }
      }
    })
  }

  fun popupRequested(breakpoint: Any?): Boolean {
    if (myBalloonToHide != null && !myBalloonToHide!!.isDisposed()) {
      return true
    }
    return selectInDialogShowing(breakpoint)
  }

  fun showDialog(initialBreakpoint: Any?) {
    if (selectInDialogShowing(initialBreakpoint)) return

    val dialog: BreakpointsDialog = object : BreakpointsDialog(myProject,
                                                               if (initialBreakpoint != null) initialBreakpoint else myBreakpoint) {
      override fun dispose() {
        myBreakpoint = null
        myDialogShowing = null

        super.dispose()
      }
    }

    if (myBalloonToHide != null) {
      if (!myBalloonToHide!!.isDisposed()) {
        myBalloonToHide!!.hide()
      }
      myBalloonToHide = null
    }
    myDialogShowing = dialog

    dialog.show()
  }

  private fun selectInDialogShowing(initialBreakpoint: Any?): Boolean {
    if (myDialogShowing != null) {
      val window = myDialogShowing!!.getWindow()
      if (window != null && window.isDisplayable()) { // workaround for IDEA-197804
        myDialogShowing!!.selectBreakpoint(initialBreakpoint, true)
        myDialogShowing!!.toFront()
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
