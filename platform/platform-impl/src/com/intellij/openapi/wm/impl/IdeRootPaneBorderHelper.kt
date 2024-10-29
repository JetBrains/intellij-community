// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.application.Application
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import java.awt.Frame
import java.awt.event.WindowStateListener
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFrame
import javax.swing.JRootPane
import javax.swing.JRootPane.NONE
import javax.swing.UIManager

/**
 * Handles a decorated border on a root pane if needed
 */
internal object IdeRootPaneBorderHelper {
  /**
   * Sets up the border from the current state of the IDE and installs various listeners which will update the border as needed
   * Requires a fully set-up application
   *
   * Relies on [IdeFrameDecorator.FULL_SCREEN] in [rootPane] to acquire and listen to fullscreen state
   * [frameDecorator] is passed mainly to convey that fact and avoid adding a listener if the state never changes
   */
  fun install(application: Application, cs: CoroutineScope, frame: JFrame, frameDecorator: IdeFrameDecorator?, rootPane: JRootPane) {
    if (SystemInfoRt.isXWindow) {
      installLinux(application, cs, frame, frameDecorator, rootPane)
    }
    else {
      rootPane.border = UIManager.getBorder("Window.border")
    }
  }

  private fun installLinux(
    application: Application,
    cs: CoroutineScope,
    frame: JFrame,
    frameDecorator: IdeFrameDecorator?,
    rootPane: JRootPane,
  ) {
    val fullScreen = AtomicReference<Boolean>(frameDecorator?.isInFullScreen == true)
    application.messageBus.connect(cs).subscribe(LafManagerListener.TOPIC, LafManagerListener {
      if (rootPane.windowDecorationStyle == NONE) {
        installLinuxBorder(rootPane, UISettings.getInstance(), fullScreen.get(), frame.extendedState)
      }
    })
    application.messageBus.connect(cs).subscribe(UISettingsListener.TOPIC, UISettingsListener {
      installLinuxBorder(rootPane, it, fullScreen.get(), frame.extendedState)
    })

    val windowStateListener = WindowStateListener {
      installLinuxBorder(rootPane, UISettings.getInstance(), fullScreen.get(), it.newState)
    }
    frame.addWindowStateListener(windowStateListener)
    cs.coroutineContext.job.invokeOnCompletion {
      frame.removeWindowStateListener(windowStateListener)
    }

    // TODO: convert to a listener on IdeFrameDecorator
    if (frameDecorator != null) {
      rootPane.addPropertyChangeListener(IdeFrameDecorator.FULL_SCREEN) {
        fullScreen.set(frameDecorator.isInFullScreen)
        installLinuxBorder(rootPane, UISettings.getInstance(), fullScreen.get(), frame.extendedState)
      }
    }

    installLinuxBorder(rootPane, UISettings.getInstance(), fullScreen.get(), frame.extendedState)
  }

  private fun installLinuxBorder(rootPane: JRootPane, uiSettings: UISettings, isFullScreen: Boolean, frameState: Int) {
    if (SystemInfoRt.isXWindow) {
      val maximized = frameState and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH
      val undecorated = !isFullScreen && !maximized && CustomWindowHeaderUtil.hideNativeLinuxTitle(uiSettings)
      rootPane.border = JBUI.CurrentTheme.Window.getBorder(undecorated)
    }
  }
}