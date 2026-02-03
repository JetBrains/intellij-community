// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.KeyboardFocusManager
import javax.swing.JComponent
import javax.swing.SwingUtilities

object UiUtils {
  const val DEFAULT_BUTTON_WIDTH: Int = 280
  const val DEFAULT_BUTTON_HEIGHT: Int = 40
  val POPUP = Key<Boolean>("ImportSetting_OtherOptions_POPUP")
  val DESCRIPTION = Key<@NlsSafe String>("ImportSetting_ProductDescription")
  val HEADER_FONT: JBFont = JBFont.label().biggerOn(7f)
  val HEADER_BORDER = JBUI.Borders.empty(18, 0, 17, 0)
  val CARD_BORDER = JBUI.Borders.empty(0, 19, 13, 19)

  fun isFocusOwner(container: JComponent): Boolean {
    val focussed = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
    return (focussed == null || !SwingUtilities.isDescendingFrom(container, focussed))
  }

  val prefButtonSize = JBDimension(DEFAULT_BUTTON_WIDTH, DEFAULT_BUTTON_HEIGHT)
}