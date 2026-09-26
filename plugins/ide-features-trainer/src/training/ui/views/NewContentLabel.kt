// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.ui.views

import com.intellij.ui.paint.RectanglePainter
import com.intellij.util.ui.JBUI
import training.learn.LearnBundle
import training.ui.UISettings
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JLabel
import javax.swing.border.EmptyBorder

class NewContentLabel : JLabel(LearnBundle.message("new.content.marker.text")) {
  init {
    foreground = UISettings.getInstance().newContentForegroundColor
    border = EmptyBorder(0, JBUI.scale(5), 0, JBUI.scale(5))
    font = UISettings.getInstance().getFont(-2)
  }

  override fun paint(g: Graphics) {
    val g2 = g.create() as Graphics2D
    g2.color = UISettings.getInstance().newContentBackgroundColor
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
    RectanglePainter.FILL.paint(g2, 0, 0, width, height, height)
    super.paint(g)
  }
}