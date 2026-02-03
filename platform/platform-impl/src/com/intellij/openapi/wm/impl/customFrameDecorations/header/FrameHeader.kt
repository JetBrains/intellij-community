// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.UISettings
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.wm.impl.customFrameDecorations.frameButtons.CustomFrameButtons
import com.intellij.openapi.wm.impl.customFrameDecorations.frameButtons.LinuxResizableCustomFrameButtons
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil.hideNativeLinuxTitle
import com.intellij.util.ui.JBFont
import java.awt.Font
import java.awt.Frame
import java.awt.Toolkit
import javax.swing.JFrame
import javax.swing.JPopupMenu
import javax.swing.JSeparator

internal abstract class FrameHeader(protected val frame: JFrame) : CustomHeader(frame) {
  private val iconifyAction = CustomFrameAction(ActionsBundle.message("action.MinimizeCurrentWindow.text"),
                                                        AllIcons.Windows.MinimizeSmall) { iconify() }
  private val restoreAction = CustomFrameAction(CommonBundle.message("button.without.mnemonic.restore"),
                                                        AllIcons.Windows.RestoreSmall) { restore() }
  private val maximizeAction = CustomFrameAction(IdeBundle.message("action.maximize.text"),
                                                         AllIcons.Windows.MaximizeSmall) { maximize() }

  protected var state = 0

  @Suppress("LeakingThis")
  private val closeAction = createCloseAction(this)

  protected val buttonPanes: CustomFrameButtons? by lazy {
    createButtonsPane()
  }

  override fun windowStateChanged() {
    super.windowStateChanged()
    updateActions()
  }

  override fun updateActive() {
    super.updateActive()
    buttonPanes?.onUpdateFrameActive()
  }

  private fun iconify() {
    frame.extendedState = state or Frame.ICONIFIED
  }

  private fun maximize() {
    frame.extendedState = state or Frame.MAXIMIZED_BOTH
  }

  private fun restore() {
    if (state and Frame.ICONIFIED != 0) {
      frame.extendedState = state and Frame.ICONIFIED.inv()
    }
    else {
      frame.extendedState = state and Frame.MAXIMIZED_BOTH.inv()
    }
  }

  override fun addNotify() {
    super.addNotify()
    updateActions()
  }

  private fun updateActions() {
    state = frame.extendedState
    if (frame.isResizable) {
      if (state and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH) {
        maximizeAction.isEnabled = false
        restoreAction.isEnabled = true
      }
      else {
        maximizeAction.isEnabled = true
        restoreAction.isEnabled = false
      }
    }
    else {
      maximizeAction.isEnabled = false
      restoreAction.isEnabled = false
    }
    iconifyAction.isEnabled = true
    closeAction.isEnabled = true

    buttonPanes?.updateVisibility()
    updateCustomTitleBar()
  }

  override fun addMenuItems(menu: JPopupMenu) {
    menu.add(restoreAction).apply { font = JBFont.create(font, false) }
    menu.add(iconifyAction).apply { font = JBFont.create(font, false) }
    if (Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH)) {
      menu.add(maximizeAction).apply { font = JBFont.create(font, false) }
    }

    menu.add(JSeparator())

    val closeMenuItem = menu.add(closeAction)
    closeMenuItem.font = JBFont.label().deriveFont(Font.BOLD)
  }

  private fun createButtonsPane(): CustomFrameButtons? {
    if (hideNativeLinuxTitle(UISettings.shadowInstance)) {
      return LinuxResizableCustomFrameButtons.create(closeAction, restoreAction, iconifyAction, maximizeAction)
    }
    return null
  }
}