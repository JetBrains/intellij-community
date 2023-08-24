// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DialogTitleCapitalization")

package com.intellij.internal.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.X11UiUtil
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.Alarm
import javax.swing.JComponent
import javax.swing.JLabel

internal class X11UiTestAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    FullScreenTestDialog(e.project, templatePresentation.text).show()
  }
}

private const val UPDATE_INTERVAL = 500

private class FullScreenTestDialog(val project: Project?, dialogTitle: String) :
  DialogWrapper(project, null, true, IdeModalityType.MODELESS, false) {

  private val alarm = Alarm(disposable)
  private lateinit var update: Runnable
  private lateinit var lbIsInFullScreenMode: JLabel
  private lateinit var lbIdeFrameInFullScreen: JLabel

  init {
    title = dialogTitle
    init()
  }

  override fun createCenterPanel(): JComponent {
    if (!SystemInfoRt.isXWindow) {
      return panel {
        row { label("Available for Linux only") }
      }
    }

    val result = panel {
      group("X11UiUtil Values") {
        row("isFullScreenSupported:") {
          label(X11UiUtil.isFullScreenSupported().toString())
        }
        row("isInFullScreenMode:") {
          lbIsInFullScreenMode = label("").component
        }
        row("isTileWM:") {
          label(X11UiUtil.isTileWM().toString())
        }
        row("isWSL:") {
          label(X11UiUtil.isWSL().toString())
        }
      }

      group("Misc Values") {
        row("IdeFrame.isInFullScreen:") {
          lbIdeFrameInFullScreen = label("").component
        }
      }

      group("Actions") {
        row {
          button("toggleFullScreenMode") {
            getFrame()?.let {
              X11UiUtil.toggleFullScreenMode(it)
            }
          }.comment("This action brings IDE to inconsistent state with FullScreenMode")
        }
      }
    }
    update = Runnable {
      timerUpdate()
      alarm.addRequest(update, UPDATE_INTERVAL)
    }
    alarm.addRequest(update, UPDATE_INTERVAL)
    return result
  }

  private fun timerUpdate() {
    val frame = getFrame()
    lbIsInFullScreenMode.text = if (frame == null) "IdeFrame not found" else X11UiUtil.isInFullScreenMode(frame).toString()
    lbIdeFrameInFullScreen.text = if (frame == null) "IdeFrame not found" else frame.isInFullScreen.toString()
  }

  private fun getFrame(): IdeFrameImpl? {
    return WindowManagerEx.getInstanceEx().getFrame(project)
  }
}
