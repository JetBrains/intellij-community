// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge.dialog

import com.intellij.icons.AllIcons.Actions
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.ui.InplaceButton
import com.intellij.ui.components.JBLayeredPane
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.event.*
import javax.swing.JButton
import javax.swing.JLayeredPane

internal class OptionButton<T>(val option: T,
                               @NonNls val flag: String,
                               val removeClickListener: () -> Unit) : JBLayeredPane() {

  private val flagBtn = createFlagButton()
  private val removeBtn = createCloseButton()
  private val evtListener = createEventListener()

  init {
    add(flagBtn, JLayeredPane.DEFAULT_LAYER, 0)
    add(removeBtn, JLayeredPane.POPUP_LAYER, 1)

    UiNotifyConnector(this, object : Activatable {
      override fun showNotify() {
        Toolkit.getDefaultToolkit()
          .addAWTEventListener(evtListener, AWTEvent.MOUSE_MOTION_EVENT_MASK or AWTEvent.MOUSE_EVENT_MASK)
      }

      override fun hideNotify() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(evtListener)
      }
    })
  }

  override fun doLayout() {
    super.doLayout()
    layoutButtons()
  }

  override fun getMinimumSize() = size

  override fun getPreferredSize() = size

  override fun getSize(): Dimension {
    val btnSize = flagBtn.preferredSize
    val insets = flagBtn.border.getBorderInsets(flagBtn)
    val removeBtnSize = JBUI.scale(16)

    return Dimension(btnSize.width + removeBtnSize / 2 - insets.right,
                     btnSize.height + removeBtnSize / 2 - insets.top)
  }

  private fun createFlagButton() = object : JButton(flag) {
    override fun paintComponent(g: Graphics) {
      putClientProperty("JButton.borderColor", if (hasFocus()) null else getBackgroundColor())
      super.paintComponent(g)
    }
  }.apply {
    preferredSize = JBDimension(preferredSize.width, JBUI.scale(30), true)

    putClientProperty("JButton.backgroundColor", getBackgroundColor())
    addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent) {
        if (KeyEvent.VK_BACK_SPACE == e.keyCode || KeyEvent.VK_DELETE == e.keyCode) {
          removeClickListener()
        }
      }
    })
  }

  private fun createCloseButton() = InplaceButton(
    IconButton(GitBundle.message("merge.option.remove"), Actions.DeleteTag, Actions.DeleteTagHover),
    ActionListener { removeClickListener() }).apply {
    isVisible = false
    isOpaque = false
  }

  private fun createEventListener() = AWTEventListener { event ->
    val me = event as MouseEvent
    val component = me.component
    if (component === flagBtn || component === removeBtn || component === this@OptionButton) {
      if (MouseEvent.MOUSE_ENTERED == me.id || MouseEvent.MOUSE_MOVED == me.id) {
        removeBtn.isVisible = true
      }
    }
    else if (MouseEvent.MOUSE_MOVED == me.id) {
      removeBtn.isVisible = false
    }
  }

  private fun layoutButtons() {
    val btnSize = flagBtn.preferredSize
    val insets = flagBtn.border.getBorderInsets(flagBtn)
    val removeBtnSize = JBUI.scale(16)

    flagBtn.setBounds(0, removeBtnSize / 2 - insets.top, btnSize.width, btnSize.height)
    removeBtn.setBounds(btnSize.width - removeBtnSize / 2 - insets.right, 0, removeBtnSize, removeBtnSize)
  }

  private fun getBackgroundColor() = JBUI.CurrentTheme.ActionButton.hoverBackground()
}