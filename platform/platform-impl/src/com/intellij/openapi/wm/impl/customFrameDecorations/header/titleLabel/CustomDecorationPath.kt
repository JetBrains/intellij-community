// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel

import com.intellij.openapi.Disposable
import java.awt.Rectangle

class CustomDecorationPath() : SelectedEditorFilePath() {
  fun getListenerBounds(): List<Rectangle> {
    val mouseInsets = 2
    val projectLabelRect = getView().bounds

    return if (isClipped()) {
      emptyList()
    }
    else {
      listOf(
        Rectangle(projectLabelRect.x, projectLabelRect.y, mouseInsets, projectLabelRect.height),
        Rectangle(projectLabelRect.x, projectLabelRect.y, projectLabelRect.width, mouseInsets),
        Rectangle(projectLabelRect.x, projectLabelRect.maxY.toInt() - mouseInsets, projectLabelRect.width, mouseInsets),
        Rectangle(projectLabelRect.maxX.toInt() - mouseInsets, projectLabelRect.y, mouseInsets, projectLabelRect.height)
      )
    }
  }
}