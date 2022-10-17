// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge.dialog

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil.BW
import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.*
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.geom.Line2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RectangularShape
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.plaf.basic.ComboPopup

/**
 * ComboBox UI that enables to manager component side border and outer insets.
 *
 * @param border         component border
 * @param outerInsets    component outer insets
 * @param popupEmptyText text to show when component have no options
 */
internal class FlatComboBoxUI(var border: Insets = Insets(1, 1, 1, 1),
                              var outerInsets: Insets = JBInsets.create(DarculaUIUtil.BW.get(), DarculaUIUtil.BW.get()),
                              @NlsContexts.StatusText private val popupEmptyText: String = StatusText.getDefaultEmptyText(),
                              private val popupComponentProvider: ((JComponent) -> JComponent)? = null)
  : DarculaComboBoxUI(0f, Insets(0, 0, 0, 0), false) {

  override fun paintArrow(g2: Graphics2D, btn: JButton) {
    g2.color = JBUI.CurrentTheme.Arrow.foregroundColor(comboBox.isEnabled)

    val r = Rectangle(btn.size)
    JBInsets.removeFrom(r, JBUI.insets(1, 0, 1, 1))

    val tW = JBUIScale.scale(9)
    val tH = JBUIScale.scale(5)

    val xU = (r.width - tW) / 2 - JBUIScale.scale(1)
    val yU = (r.height - tH) / 2 + JBUIScale.scale(1)

    val leftLine = Line2D.Float(xU.toFloat(), yU.toFloat(),
                                xU.toFloat() + tW.toFloat() / 2f, yU.toFloat() + tH.toFloat())

    val rightLine = Line2D.Float(xU.toFloat() + tW.toFloat() / 2f, yU.toFloat() + tH.toFloat(),
                                 xU.toFloat() + tW.toFloat(), yU.toFloat())

    g2.draw(leftLine)
    g2.draw(rightLine)
  }

  override fun getOuterShape(r: Rectangle, bw: Float, arc: Float): RectangularShape {
    if (hasFocus) {
      val tunedBw = if (UIUtil.isUnderDefaultMacTheme()) macOsBw.float else bw
      return super.getOuterShape(r, tunedBw, arc)
    }
    return Rectangle2D.Float(outerInsets.left.toFloat(),
                             outerInsets.top.toFloat(),
                             r.width - outerInsets.left.toFloat() - outerInsets.right.toFloat(),
                             r.height - outerInsets.top.toFloat() - outerInsets.bottom.toFloat())
  }

  override fun getInnerShape(r: Rectangle, bw: Float, lw: Float, arc: Float): RectangularShape {
    if (hasFocus) {
      val tunedBw = if (UIUtil.isUnderDefaultMacTheme()) macOsBw.float else bw
      return super.getInnerShape(r, tunedBw, lw, arc)
    }
    return Rectangle2D.Float(outerInsets.left + lw * border.left,
                             outerInsets.top + lw,
                             r.width - (outerInsets.left + lw * border.left) - (outerInsets.right + lw * border.right),
                             r.height - (outerInsets.top + lw) - (outerInsets.bottom + lw))
  }

  override fun getBorderInsets(c: Component?) = outerInsets

  override fun createPopup(): ComboPopup {
    return MyComboBoxPopup(comboBox).apply {
      configureList(list)
      configurePopupComponent(popupComponentProvider)
    }
  }

  private fun configureList(list: JList<*>) {
    (list as? ComponentWithEmptyText)?.let {
      it.emptyText.text = popupEmptyText
    }
  }

  private class MyComboBoxPopup(comboBox: JComboBox<*>) : CustomComboPopup(comboBox) {

    fun configurePopupComponent(popupComponentProvider: ((JComponent) -> JComponent)? = null) {
      val popupComponent = popupComponentProvider?.invoke(scroller)
      if (popupComponent != null) {
        removeAll()
        add(popupComponent)
      }
    }
  }

  // DarculaUIUtil.BW in case of native MacOS theme is 4 (defined in macintellijlaf.theme.json -> Component.focusWidth
  // Outline focus width in case of MacOS theme is 3 (hardcoded int com.intellij.ide.ui.laf.darcula.DarculaUIUtil.doPaint)
  // Compensation border of FlatComboBoxUI is 0, not 1 as in Darcula and MacOs, so we need to align it
  private val macOsBw: JBValue.Float = JBValue.Float(BW.unscaled - DEFAULT_BORDER_COMPENSATION)
}