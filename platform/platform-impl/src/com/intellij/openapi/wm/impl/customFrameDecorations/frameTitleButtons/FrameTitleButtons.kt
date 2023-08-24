// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.frameTitleButtons

import com.intellij.openapi.wm.impl.customFrameDecorations.TitleButtonsPanel
import java.awt.Dimension
import javax.swing.Action
import javax.swing.Icon
import javax.swing.JButton

interface FrameTitleButtons {
  val closeButton: JButton
  val restoreButton: JButton?
  val maximizeButton: JButton?
  val minimizeButton: JButton?

  val restoreIcon: Icon
  val restoreInactiveIcon: Icon

  val maximizeIcon: Icon
  val maximizeInactiveIcon: Icon

  val minimizeIcon: Icon
  val minimizeInactiveIcon: Icon

  val closeIcon: Icon
  val closeInactiveIcon: Icon
  val closeHoverIcon: Icon

  fun createButton(accessibleName: String, action: Action): JButton
  fun fillButtonPane(panel: TitleButtonsPanel)
  fun setScaledPreferredSize(size: Dimension): Dimension
}