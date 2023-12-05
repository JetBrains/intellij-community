// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeFrame
import org.jetbrains.annotations.ApiStatus
import java.awt.Frame
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.SwingUtilities

@ApiStatus.Internal
@ApiStatus.Experimental
class WindowDeactivationManager {
  companion object {
    @JvmStatic
    fun getInstance(): WindowDeactivationManager = service()
  }

  fun addWindowDeactivationListener(window: Window, project: Project, disposable: Disposable, onWindowDeactivated: Runnable) {
    val focusListener = object : WindowAdapter() {
      override fun windowGainedFocus(e: WindowEvent?) {
        onWindowDeactivated.run()
      }
    }

    window.addWindowListener(object : WindowAdapter() {
      var wasOpened = false

      override fun windowDeactivated(e: WindowEvent) {
        if (!wasOpened) {
          return
        }
        // At the moment of deactivation there is just "temporary" focus owner (main frame),
        // true focus owner (Search Everywhere popup etc.) appears later so the check should be postponed too
        ApplicationManager.getApplication().invokeLater(
          {
            val focusOwner = IdeFocusManager.getInstance(project).focusOwner ?: return@invokeLater
            if (!SwingUtilities.isDescendingFrom(focusOwner, window)) {
              onWindowDeactivated.run()
            }
          },
          ModalityState.current()
        )
      }

      override fun windowOpened(e: WindowEvent?) {
        // Currently, in WSLg environment dialog showing generates focus events corresponding to dialog getting focus,
        // then losing it to main frame, then getting it again immediately. As a workaround, we track focus/activation events only after
        // 'window opened' event is received.
        // Another case when such delaying can make sense is when the dialog is showing at the same time some popup is closing
        // (e.g. invoking 'Find in Files...' from a quick list).
        wasOpened = true

        Frame.getFrames().asSequence().filter { it is IdeFrame && it.project === project }.forEach {
          if (Disposer.tryRegister(disposable) { it.removeWindowFocusListener(focusListener) }) {
            it.addWindowFocusListener(focusListener)
          }
        }
      }
    })
  }
}