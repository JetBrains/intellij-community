// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.frameTitleButtons

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.wm.impl.customFrameDecorations.LinuxLookAndFeel
import com.intellij.openapi.wm.impl.customFrameDecorations.TitleButtonsPanel
import com.intellij.openapi.wm.impl.customFrameDecorations.style.HOVER_KEY
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.accessibility.AccessibleContext
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.plaf.ButtonUI
import javax.swing.plaf.basic.BasicButtonUI


class LinuxFrameTitleButtons(
  myCloseAction: Action,
  myRestoreAction: Action? = null,
  myIconifyAction: Action? = null,
  myMaximizeAction: Action? = null) : FrameTitleButtons {
  override val closeButton: JButton = createButton("Close", myCloseAction)
  override val restoreButton: JButton? = myRestoreAction?.let { createButton("Restore", it) }
  override val maximizeButton: JButton? = myMaximizeAction?.let { createButton("Maximize", it) }
  override val minimizeButton: JButton? = myIconifyAction?.let { createButton("Iconify", it) }



  val restore = LinuxLookAndFeel.getLinuxIcon("window-restore-symbolic.svg") ?: AllIcons.Windows.Restore
  override val restoreIcon = restore
  override val restoreInactiveIcon = restore

  val maximize = LinuxLookAndFeel.getLinuxIcon("window-maximize-symbolic.svg") ?: AllIcons.Windows.Maximize
  override val maximizeIcon = maximize
  override val maximizeInactiveIcon = maximize

  val minimize = LinuxLookAndFeel.getLinuxIcon("window-minimize-symbolic.svg") ?: AllIcons.Windows.Minimize
  override val minimizeIcon = minimize
  override val minimizeInactiveIcon = minimize

  val close = LinuxLookAndFeel.getLinuxIcon("window-close-symbolic.svg") ?: AllIcons.Windows.CloseActive
  override val closeIcon = close
  override val closeInactiveIcon = close
  override val closeHoverIcon = close

  override fun createButton(accessibleName: String, action: Action): JButton {
    val button = object : JButton() {
      init {
        super.setUI(HoveredCircleButtonUI())
      }

      override fun setUI(ui: ButtonUI?) {
      }
    }
    button.action = action
    button.isFocusable = false
    button.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, accessibleName)
    button.text = null
    return button
  }

  override fun fillButtonPane(panel: TitleButtonsPanel) {
    var linuxButtonsLayout = LinuxLookAndFeel.getHeaderLayout()
    if (!linuxButtonsLayout.contains("close")) {
      linuxButtonsLayout = linuxButtonsLayout.plus("close")
    }
    for (item in linuxButtonsLayout) {
      when (item) {
        "minimize" -> this.minimizeButton?.let { panel.addComponent(it) }
        "maximize" -> {
          this.maximizeButton?.let { panel.addComponent(it) }
          this.restoreButton?.let { panel.addComponent(it) }
        }
        "close" -> panel.addComponent(this.closeButton)
      }
    }
    panel.border = JBUI.Borders.emptyRight(1)
  }


  override fun setScaledPreferredSize(size: Dimension): Dimension {
    return Dimension(38, size.height)
  }
}


// Can not use JBColor because title frame can use a dark background in Light theme
@Suppress("UseJBColor")
private class HoveredCircleButtonUI : BasicButtonUI() {
  private val circleDiameter = 24

  // Adwaita background circle:
  // - light theme: rgba(0, 0, 0, 0.08) -> 20.40
  // - dark theme: rgba(255, 255, 255, 0.1) -> 25.5
  private val circleLightBackground = Color(1f, 1f, 1f, 0.08f)
  private val circleDarkBackground = Color(0f, 0f, 0f, 0.1f)

  override fun paint(g: Graphics, c: JComponent) {
    val isLightBackground = with(LafManager.getInstance().currentLookAndFeel.name) {
      this == "Light with Light Header" || this == "IntelliJ Light"
    }
    val backgroundColor = if (isLightBackground) circleDarkBackground else circleLightBackground

    g.color = backgroundColor
    getHoverColor(c)?.let {
      g.color = alterAlpha(backgroundColor, 0.05f)
    }

    if (g is Graphics2D) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.fillRoundRect((c.width / 2) - (circleDiameter / 2), (c.height / 2) - (circleDiameter / 2), circleDiameter, circleDiameter, c.width, c.height)
    }
    super.paint(g, c)
  }

  private fun getHoverColor(c: JComponent): Color? = c.getClientProperty(HOVER_KEY) as? Color

  private fun alterAlpha(color: Color, amount: Float): Color {
    return Color(color.red, color.green, color.blue, (((color.alpha.toFloat() / 256f) + amount) * 256).toInt())
  }
}

