// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations

import com.intellij.icons.AllIcons
import com.intellij.openapi.wm.impl.customFrameDecorations.style.StyleManager
import javax.swing.Action

internal class ResizableCustomFrameTitleButtons(closeAction: Action,
                                                private val myRestoreAction: Action,
                                                private val myIconifyAction: Action,
                                                private val myMaximizeAction: Action
) : CustomFrameTitleButtons(closeAction) {
  companion object {
    private val restoreIcon = AllIcons.Windows.Restore
    private val restoreInactiveIcon = AllIcons.Windows.RestoreInactive

    private val maximizeIcon = AllIcons.Windows.Maximize
    private val maximizeInactiveIcon = AllIcons.Windows.MaximizeInactive

    private val minimizeIcon = AllIcons.Windows.Minimize
    private val minimizeInactiveIcon = AllIcons.Windows.MinimizeInactive

    fun create(myCloseAction: Action,
               myRestoreAction: Action,
               myIconifyAction: Action,
               myMaximizeAction: Action): ResizableCustomFrameTitleButtons {
      val darculaTitleButtons = ResizableCustomFrameTitleButtons(myCloseAction, myRestoreAction, myIconifyAction, myMaximizeAction)
      darculaTitleButtons.createChildren()
      return darculaTitleButtons
    }
  }

  private val restoreButton = createButton("Restore", myRestoreAction)
  private val maximizeButton = createButton("Maximize", myMaximizeAction)
  private val minimizeButton = createButton("Iconify", myIconifyAction)

  override fun fillButtonPane() {
    super.fillButtonPane()
    addComponent(minimizeButton)
    addComponent(maximizeButton)
    addComponent(restoreButton)
  }

  override fun updateVisibility() {
    super.updateVisibility()
    minimizeButton.isVisible = myIconifyAction.isEnabled
    restoreButton.isVisible = myRestoreAction.isEnabled
    maximizeButton.isVisible = myMaximizeAction.isEnabled
  }

  override fun updateStyles() {
    super.updateStyles()
    StyleManager.applyStyle(restoreButton, getStyle(if (isSelected) restoreIcon else restoreInactiveIcon, restoreIcon))
    StyleManager.applyStyle(maximizeButton, getStyle(if (isSelected) maximizeIcon else maximizeInactiveIcon, maximizeIcon))
    StyleManager.applyStyle(minimizeButton, getStyle(if (isSelected) minimizeIcon else minimizeInactiveIcon, minimizeIcon))
  }
}