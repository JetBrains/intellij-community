// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations

import com.intellij.icons.AllIcons
import com.intellij.openapi.wm.impl.customFrameDecorations.style.StyleManager
import javax.swing.Action
import javax.swing.JButton

internal class ResizableCustomFrameTitleButtons(closeAction: Action,
                                                private val myRestoreAction: Action,
                                                private val myIconifyAction: Action,
                                                private val myMaximizeAction: Action
) : CustomFrameTitleButtons(closeAction) {
  companion object{
    private val restoreIcon = freezeIconUserSize(AllIcons.Windows.Restore)
    private val restoreInactiveIcon = freezeIconUserSize(AllIcons.Windows.RestoreInactive)

    private val maximizeIcon = freezeIconUserSize(AllIcons.Windows.Maximize)
    private val maximizeInactiveIcon = freezeIconUserSize(AllIcons.Windows.MaximizeInactive)

    private val minimizeIcon = freezeIconUserSize(AllIcons.Windows.Minimize)
    private val minimizeInactiveIcon = freezeIconUserSize(AllIcons.Windows.MinimizeInactive)

    fun create(myCloseAction: Action, myRestoreAction: Action, myIconifyAction: Action, myMaximizeAction: Action) : ResizableCustomFrameTitleButtons {
      val darculaTitleButtons = ResizableCustomFrameTitleButtons(myCloseAction, myRestoreAction, myIconifyAction, myMaximizeAction)
      darculaTitleButtons.createChildren()
      return darculaTitleButtons
    }
  }

  val restoreButton: JButton = createButton("Restore", myRestoreAction)
  val maximizeButton: JButton = createButton("Maximize", myMaximizeAction)
  val minimizeButton: JButton = createButton("Iconify", myIconifyAction)

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
    StyleManager.applyStyle(restoreButton, getStyle(if(isSelected) restoreIcon else restoreInactiveIcon, restoreIcon))
    StyleManager.applyStyle(maximizeButton, getStyle(if(isSelected) maximizeIcon else maximizeInactiveIcon, maximizeIcon))
    StyleManager.applyStyle(minimizeButton, getStyle(if(isSelected) minimizeIcon else minimizeInactiveIcon, minimizeIcon))
  }


}