// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.frameButtons

import com.intellij.openapi.wm.impl.WindowButtonsConfiguration
import javax.swing.Action

internal class LinuxResizableCustomFrameButtons(closeAction: Action,
                                                private val myRestoreAction: Action,
                                                private val myIconifyAction: Action,
                                                private val myMaximizeAction: Action
) : LinuxCustomFrameButtons(closeAction) {
  companion object {
    fun create(myCloseAction: Action,
               myRestoreAction: Action,
               myIconifyAction: Action,
               myMaximizeAction: Action): CustomFrameButtons {
      val darculaTitleButtons = LinuxResizableCustomFrameButtons(myCloseAction, myRestoreAction, myIconifyAction, myMaximizeAction)
      darculaTitleButtons.createChildren()
      return darculaTitleButtons
    }
  }

  private val restoreButton = FrameButton(myRestoreAction, FrameButton.Type.RESTORE)
  private val maximizeButton = FrameButton(myMaximizeAction, FrameButton.Type.MAXIMIZE)
  private val minimizeButton = FrameButton(myIconifyAction, FrameButton.Type.MINIMIZE)

  override fun createChildren() {
    fillContent(WindowButtonsConfiguration.getInstance()?.state)
    updateVisibility()
  }

  override fun onUpdateFrameActive() {
    super.onUpdateFrameActive()

    minimizeButton.updateStyle()
    restoreButton.updateStyle()
    maximizeButton.updateStyle()
  }

  override fun updateVisibility() {
    super.updateVisibility()

    minimizeButton.isVisible = myIconifyAction.isEnabled
    restoreButton.isVisible = myRestoreAction.isEnabled
    maximizeButton.isVisible = myMaximizeAction.isEnabled
  }

  fun fillContent(state: WindowButtonsConfiguration.State?) {
    getContent().removeAll()

    val buttons: List<WindowButtonsConfiguration.WindowButton> =
      state?.buttons
      ?: listOf(WindowButtonsConfiguration.WindowButton.MINIMIZE, WindowButtonsConfiguration.WindowButton.MAXIMIZE, WindowButtonsConfiguration.WindowButton.CLOSE)
    for (button in buttons) {
      when (button) {
        WindowButtonsConfiguration.WindowButton.MINIMIZE -> addComponent(minimizeButton)
        WindowButtonsConfiguration.WindowButton.MAXIMIZE -> {
          addComponent(maximizeButton)
          addComponent(restoreButton)
        }
        WindowButtonsConfiguration.WindowButton.CLOSE -> addComponent(closeButton)
      }
    }
  }

  fun updateTheme(theme: String?) {
    closeButton.updateTheme(theme)
    minimizeButton.updateTheme(theme)
    restoreButton.updateTheme(theme)
    maximizeButton.updateTheme(theme)
  }
}
