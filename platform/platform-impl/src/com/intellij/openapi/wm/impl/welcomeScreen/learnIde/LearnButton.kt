// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.ui.util.preferredHeight
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JButton

@ApiStatus.Internal
class LearnButton : JButton {
  constructor(buttonAction: Action, contentEnabled: Boolean) : super() {
    action = buttonAction
    action = buttonAction
    margin = JBUI.emptyInsets()
    isOpaque = false
    isContentAreaFilled = false
    isEnabled = contentEnabled
    preferredHeight = JBUI.scale(33)
  }
  constructor(anAction: AnAction, @Nls title: String, contentEnabled: Boolean) : this(anAction.toSwingAction(title), contentEnabled) {}

  companion object {
    private fun AnAction.toSwingAction(@Nls title: String) = object : AbstractAction(title) {
      override fun actionPerformed(e: ActionEvent?) {
        performActionOnWelcomeScreen(this@toSwingAction)
      }
    }
    fun performActionOnWelcomeScreen(action: AnAction) {
      val anActionEvent = AnActionEvent.createFromAnAction(action, null, ActionPlaces.WELCOME_SCREEN, DataContext.EMPTY_CONTEXT)
      ActionUtil.performAction(action, anActionEvent)
    }
  }


}
