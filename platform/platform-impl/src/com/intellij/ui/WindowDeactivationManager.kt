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
    Frame.getFrames().filter { it is IdeFrame && it.project == project}.forEach {
      it.addWindowFocusListener(focusListener)
      Disposer.register(disposable) {
        it.removeWindowFocusListener(focusListener)
      }
    }

    window.addWindowListener(object : WindowAdapter() {
      override fun windowDeactivated(e: WindowEvent) {
        // At the moment of deactivation there is just "temporary" focus owner (main frame),
        // true focus owner (Search Everywhere popup etc.) appears later so the check should be postponed too
        ApplicationManager.getApplication().invokeLater({
          val focusOwner = IdeFocusManager.getInstance(project).focusOwner ?: return@invokeLater
          if (SwingUtilities.isDescendingFrom(focusOwner, window)) return@invokeLater
          onWindowDeactivated.run()
        }, ModalityState.current())
      }
    })
  }
}