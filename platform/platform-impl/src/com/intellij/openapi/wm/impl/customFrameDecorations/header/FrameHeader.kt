// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.wm.impl.customFrameDecorations.CustomFrameTitleButtons
import com.intellij.openapi.wm.impl.customFrameDecorations.ResizableCustomFrameTitleButtons
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.util.ui.JBFont
import com.jetbrains.CustomWindowDecoration.*
import java.awt.Font
import java.awt.Frame
import java.awt.Toolkit
import java.awt.event.WindowAdapter
import java.awt.event.WindowStateListener
import javax.swing.*

internal open class FrameHeader(protected val frame: JFrame) : CustomHeader(frame) {
  private val iconifyAction: Action = CustomFrameAction(ActionsBundle.message("action.MinimizeCurrentWindow.text"),
                                                        AllIcons.Windows.MinimizeSmall) { iconify() }
  private val restoreAction: Action = CustomFrameAction(CommonBundle.message("button.without.mnemonic.restore"),
                                                        AllIcons.Windows.RestoreSmall) { restore() }
  private val maximizeAction: Action = CustomFrameAction(IdeBundle.message("action.maximize.text"),
                                                         AllIcons.Windows.MaximizeSmall) { maximize() }

  private var windowStateListener: WindowStateListener
  protected var myState = 0

  init {
    windowStateListener = object : WindowAdapter() {
      override fun windowStateChanged(e: java.awt.event.WindowEvent?) {
        updateActions()
      }
    }
  }

  override fun createButtonsPane(): CustomFrameTitleButtons {
    return ResizableCustomFrameTitleButtons.create(myCloseAction,
                                                   restoreAction, iconifyAction,
                                                   maximizeAction)
  }


  override fun windowStateChanged() {
    super.windowStateChanged()
    updateActions()
  }

  private fun iconify() {
    frame.extendedState = myState or Frame.ICONIFIED
  }

  private fun maximize() {
    frame.extendedState = myState or Frame.MAXIMIZED_BOTH
  }

  private fun restore() {
    if (myState and Frame.ICONIFIED != 0) {
      frame.extendedState = myState and Frame.ICONIFIED.inv()
    }
    else {
      frame.extendedState = myState and Frame.MAXIMIZED_BOTH.inv()
    }
  }

  override fun addNotify() {
    super.addNotify()
    updateActions()
  }

  private fun updateActions() {
    myState = frame.extendedState
    if (frame.isResizable) {
      if (myState and Frame.MAXIMIZED_BOTH != 0) {
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
    myCloseAction.isEnabled = true

    buttonPanes.updateVisibility()
    updateCustomDecorationHitTestSpots()
  }

  override fun addMenuItems(menu: JPopupMenu) {
    menu.add(restoreAction)
    menu.add(iconifyAction)
    if (Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH)) {
      menu.add(maximizeAction)
    }

    menu.add(JSeparator())

    val closeMenuItem = menu.add(myCloseAction)
    closeMenuItem.font = JBFont.label().deriveFont(Font.BOLD)
  }

  override fun getHitTestSpots(): List<Pair<RelativeRectangle, Int>> {
    val buttons = buttonPanes as ResizableCustomFrameTitleButtons
    return listOf(
      Pair(RelativeRectangle(productIcon), OTHER_HIT_SPOT),
      Pair(RelativeRectangle(buttons.minimizeButton), MINIMIZE_BUTTON),
      Pair(RelativeRectangle(buttons.maximizeButton), MAXIMIZE_BUTTON),
      Pair(RelativeRectangle(buttons.restoreButton), MAXIMIZE_BUTTON),
      Pair(RelativeRectangle(buttons.closeButton), CLOSE_BUTTON)
    )
  }
}