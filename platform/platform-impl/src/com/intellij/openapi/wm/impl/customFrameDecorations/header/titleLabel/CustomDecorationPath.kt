// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.ui.awt.RelativeRectangle
import java.awt.Rectangle
import com.intellij.util.ui.JBUI.CurrentTheme.CustomFrameDecorations

class CustomDecorationPath : SelectedEditorFilePath() {
  fun setActive(value: Boolean) {
    val color = if (value) CustomFrameDecorations.titlePaneInfoForeground() else CustomFrameDecorations.titlePaneInactiveInfoForeground()
    projectLabel.foreground = color
    label.foreground = color
  }

  fun getListenerBounds(): List<RelativeRectangle> {
    val mouseInsets = 2
    return if (isClipped()) {
      emptyList()
    }
    else {
      listOf(
        RelativeRectangle(projectLabel, Rectangle(0, 0, mouseInsets, projectLabel.height)),
        RelativeRectangle(projectLabel, Rectangle(0, 0, projectLabel.width, mouseInsets)),
        RelativeRectangle(projectLabel,
                          Rectangle(projectLabel.x, projectLabel.width - mouseInsets, projectLabel.width, mouseInsets)),
        RelativeRectangle(projectLabel,
                          Rectangle(projectLabel.width - mouseInsets, 0, mouseInsets, projectLabel.height))
      )
    }
  }
}