// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde

import com.intellij.util.ui.JBUI
import javax.swing.Action
import javax.swing.JButton

class LearnButton(buttonAction: Action, contentEnabled: Boolean) : JButton() {

  init {
    action = buttonAction
    margin = JBUI.emptyInsets()
    isOpaque = false
    isContentAreaFilled = false
    isEnabled = contentEnabled
  }
}