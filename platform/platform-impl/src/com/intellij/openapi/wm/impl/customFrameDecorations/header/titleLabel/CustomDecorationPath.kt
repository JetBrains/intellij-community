// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.ui.awt.RelativeRectangle
import com.intellij.util.ui.JBUI.CurrentTheme.CustomFrameDecorations
import java.awt.Rectangle
import java.util.ArrayList
import javax.swing.JComponent

class CustomDecorationPath : SelectedEditorFilePath() {
  fun setActive(value: Boolean) {
    val color = if (value) CustomFrameDecorations.titlePaneInfoForeground() else CustomFrameDecorations.titlePaneInactiveInfoForeground()
    projectLabel.foreground = color
    label.foreground = color
  }

  fun getListenerBounds(): List<RelativeRectangle> {
    return if (isClipped()) {
      emptyList()
    }
    else {
      val hitTestSpots = ArrayList<RelativeRectangle>()

      hitTestSpots.addAll(getMouseInsetList(projectLabel, 1))
      hitTestSpots.addAll(getMouseInsetList(label, 1))

      hitTestSpots
    }
  }

  private fun getMouseInsetList(view: JComponent,
                                mouseInsets: Int): List<RelativeRectangle> {
    return listOf(
      RelativeRectangle(view, Rectangle(0, 0, mouseInsets, view.height)),
      RelativeRectangle(view, Rectangle(0, 0, view.width, mouseInsets)),
      RelativeRectangle(view,
                        Rectangle(0, view.height - mouseInsets, view.width, mouseInsets)),
      RelativeRectangle(view,
                        Rectangle(view.width - mouseInsets, 0, mouseInsets, view.height))
    )
  }
}