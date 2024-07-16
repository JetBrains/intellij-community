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

  private val restoreButton = LinuxFrameButton(myRestoreAction, LinuxFrameButton.Type.RESTORE)
  private val maximizeButton = LinuxFrameButton(myMaximizeAction, LinuxFrameButton.Type.MAXIMIZE)
  private val minimizeButton = LinuxFrameButton(myIconifyAction, LinuxFrameButton.Type.MINIMIZE)

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

  fun updateIconTheme(iconTheme: String?) {
    closeButton.updateIconTheme(iconTheme)
    minimizeButton.updateIconTheme(iconTheme)
    restoreButton.updateIconTheme(iconTheme)
    maximizeButton.updateIconTheme(iconTheme)
  }
}
