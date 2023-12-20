// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DialogTitleCapitalization")

package com.intellij.internal.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.X11UiUtil
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.Alarm
import com.intellij.util.ui.StartupUiUtil
import java.awt.Frame
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.reflect.KProperty0

internal class X11UiTestAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = StartupUiUtil.isXToolkit()
  }

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
  private lateinit var lbIsMaximizedVert: JLabel
  private lateinit var lbIsMaximizedHorz: JLabel
  private lateinit var lbIdeFrameInFullScreen: JLabel
  private lateinit var lbFrameExtendedState: JLabel

  init {
    title = dialogTitle
    init()
  }

  override fun createCenterPanel(): JComponent {
    update = Runnable {
      timerUpdate()
      alarm.addRequest(update, UPDATE_INTERVAL)
    }
    alarm.addRequest(update, UPDATE_INTERVAL)

    return panel {
      group("X11UiUtil Values") {
        row("isInitialized:") {
          label(X11UiUtil.isInitialized().toString())
        }
        row("isFullScreenSupported:") {
          label(X11UiUtil.isFullScreenSupported().toString())
        }
        row("isInFullScreenMode:") {
          lbIsInFullScreenMode = label("").component

          button("Toggle") {
            getFrame()?.let {
              val value = X11UiUtil.isInFullScreenMode(it)
              X11UiUtil.setFullScreenMode(it, !value)
            }
          }
        }
        row("isMaximizedVert:") {
          lbIsMaximizedVert = label("").component

          button("Set BOTH maximized") {
            getFrame()?.let {
              X11UiUtil.setMaximized(it, true)
            }
          }
        }
        row("isMaximizedHorz:") {
          lbIsMaximizedHorz = label("").component

          button("Reset BOTH maximized") {
            getFrame()?.let {
              X11UiUtil.setMaximized(it, false)
            }
          }
        }
        row("isTileWM:") {
          label(X11UiUtil.isTileWM().toString())
          contextHelp(X11UiUtil.TILE_WM.sorted().joinToString("<br>"), "Known Tile WMs")
        }
        row("isWSL:") {
          label(X11UiUtil.isWSL().toString())
            .comment("Used WSL_DISTRO_NAME env variable. Value: ${System.getenv("WSL_DISTRO_NAME") ?: ""}")
        }
      }

      group("IdeFrame") {
        row("isInFullScreen:") {
          lbIdeFrameInFullScreen = label("").component
        }
        row("extendedState:") {
          lbFrameExtendedState = label("").component

          val cb = comboBox(listOf(Frame::MAXIMIZED_VERT, Frame::MAXIMIZED_HORIZ, Frame::MAXIMIZED_BOTH),
                            SimpleListCellRenderer.create("") { it.name }).component

          button("Set `state or value`") {
            getFrame()?.let {
              @Suppress("UNCHECKED_CAST")
              val state = (cb.selectedItem as KProperty0<Int>).get()
              it.extendedState = it.extendedState or state
            }
          }
          button("Set `state and value.inv()`") {
            getFrame()?.let {
              @Suppress("UNCHECKED_CAST")
              val state = (cb.selectedItem as KProperty0<Int>).get()
              it.extendedState = it.extendedState and state.inv()
            }
          }
        }
      }

      group("Misc Values") {
        row("Current desktop:") {
          label(System.getenv("XDG_CURRENT_DESKTOP") ?: "")
            .comment("Used XDG_CURRENT_DESKTOP env variable")
        }
      }
    }
  }

  private fun timerUpdate() {
    val frame = getFrame()
    lbIsInFullScreenMode.text = if (frame == null) "IdeFrame not found" else X11UiUtil.isInFullScreenMode(frame).toString()
    lbIsMaximizedVert.text = if (frame == null) "IdeFrame not found" else X11UiUtil.isMaximizedVert(frame).toString()
    lbIsMaximizedHorz.text = if (frame == null) "IdeFrame not found" else X11UiUtil.isMaximizedHorz(frame).toString()
    lbIdeFrameInFullScreen.text = if (frame == null) "IdeFrame not found" else frame.isInFullScreen.toString()
    lbFrameExtendedState.text = if (frame == null) "IdeFrame not found" else extendedStateToString(frame.extendedState)
  }

  private fun getFrame(): IdeFrameImpl? {
    return WindowManagerEx.getInstanceEx().getFrame(project)
  }

  private fun extendedStateToString(state: Int): String {
    val horiz = state and Frame.MAXIMIZED_HORIZ != 0
    val vert = state and Frame.MAXIMIZED_VERT != 0
    return if (horiz) {
      if (vert) "MAXIMIZED_BOTH" else "MAXIMIZED_HORIZ"
    }
    else {
      if (vert) "MAXIMIZED_VERT" else "NORMAL"
    }
  }
}
