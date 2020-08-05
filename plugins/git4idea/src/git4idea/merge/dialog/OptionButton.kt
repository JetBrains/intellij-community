// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge.dialog

import com.intellij.icons.AllIcons.Actions
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.ui.InplaceButton
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import git4idea.i18n.GitBundle
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.NonNls
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.JLabel
import javax.swing.JPanel

internal class OptionButton<T>(val option: T,
                               @NonNls val flag: String,
                               val removeClickListener: () -> Unit) : JPanel() {
  init {
    background = JBUI.CurrentTheme.ActionButton.hoverBackground()

    val hInset = "${JBUI.scale(10)}px"
    val vInset = "${JBUI.scale(5)}px"

    layout = MigLayout(LC().insets(vInset, hInset, vInset, hInset).noGrid())

    val componentsHeight = "${JBUI.scale(14)}px"

    add(JLabel(flag), CC().alignY("baseline").maxHeight(componentsHeight).gapAfter("${JBUI.scale(5)}px"))
    add(createRemoveButton(), CC().alignY("baseline").height(componentsHeight))
  }

  override fun paintComponent(g: Graphics?) {
    val r = Rectangle(size)
    val shape = RoundRectangle2D.Float(r.x.toFloat() + 0.5f, r.y.toFloat() + 0.5f,
                                       r.width.toFloat() - 1f, r.height.toFloat() - 1f, 3f, 3f)

    GraphicsUtil.setupRoundedBorderAntialiasing(g)

    val g2 = g as Graphics2D
    g2.color = background
    g2.fill(shape)
  }

  private fun createRemoveButton(): InplaceButton {
    val iconButton = IconButton(GitBundle.message("merge.option.remove"), Actions.Close, Actions.CloseHovered)
    return InplaceButton(iconButton) { removeClickListener() }.apply {
      isFocusable = true

      addFocusListener(object : FocusAdapter() {
        override fun focusGained(e: FocusEvent?) {
          iconButton.setActive(true)
          repaint()
        }

        override fun focusLost(e: FocusEvent?) {
          iconButton.setActive(false)
          repaint()
        }
      })

      addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent?) {
          if (e?.keyCode == KeyEvent.VK_SPACE) {
            removeClickListener()
          }
        }
      })
    }
  }
}