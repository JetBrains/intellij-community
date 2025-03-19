/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.colorpicker

import com.intellij.ui.JBColor
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import javax.swing.JComponent

private val BORDER = JBColor(Color(0, 0, 0, 26), Color(255, 255, 255, 26))
private val BORDER_STROKE = BasicStroke(2f)

@ApiStatus.Internal
class ColorIndicator(color: Color = DEFAULT_PICKER_COLOR) : JComponent() {

  var color: Color = color
    set(value) {
      field = value
      repaint()
    }

  override fun paintComponent(g: Graphics) {
    val originalAntialiasing = (g as Graphics2D).getRenderingHint(RenderingHints.KEY_ANTIALIASING)
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val originalColor = g.color
    val originalStroke = g.stroke

    val left = insets.left
    val top = insets.top
    val circleWidth = width - insets.left - insets.right
    val circleHeight = height - insets.top - insets.bottom
    g.color = color
    g.fillOval(left, top, circleWidth, circleHeight)

    g.stroke = BORDER_STROKE
    g.color = BORDER
    g.drawOval(left, top, circleWidth, circleHeight)

    g.color = originalColor
    g.stroke = originalStroke
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, originalAntialiasing)
  }
}
