// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge.dialog

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ui.JBDimension
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class CmdLabel(@NlsSafe cmd: String,
               private val border: Insets = Insets(1, 1, 1, 1),
               private val componentSize: Dimension = JBDimension(100, 28)) : JPanel() {

  init {
    layout = MigLayout(LC().insets("0").noGrid())

    addComponent(JLabel(cmd))
  }

  fun addComponent(component: JComponent) {
    val gapY = (componentSize.height - component.preferredSize.height) / 2
    add(component,
        CC()
          .y("${gapY}px")
          .gapBefore("6"))
  }

  override fun getPreferredSize() = componentSize

  override fun paintBorder(g: Graphics) {
    val lw = DarculaUIUtil.LW.float
    val bw = DarculaUIUtil.BW.get()

    val borderShape: Path2D = Path2D.Float(Path2D.WIND_EVEN_ODD)

    borderShape.append(Rectangle2D.Float(0f, bw.toFloat(), width.toFloat(), height - (bw.toFloat()) * 2), false)
    borderShape.append(Rectangle2D.Float(lw * border.left, bw + lw,
                                         width.toFloat() - lw * border.right, height - (bw + lw) * 2), false)

    val g2 = g as Graphics2D

    g2.color = DarculaUIUtil.getOutlineColor(true, false)
    g2.fill(borderShape)
  }
}