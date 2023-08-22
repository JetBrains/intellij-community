// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.frameTitleButtons

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.wm.impl.customFrameDecorations.LinuxLookAndFeel
import com.intellij.openapi.wm.impl.customFrameDecorations.LinuxLookAndFeel.Companion.findIconAbsolutePath
import com.intellij.openapi.wm.impl.customFrameDecorations.style.HOVER_KEY
import com.intellij.ui.IconManager
import com.intellij.ui.JBColor
import com.intellij.util.IconUtil
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.accessibility.AccessibleContext
import javax.swing.Action
import javax.swing.Icon
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

  val restore = loadLinuxIcon("window-restore-symbolic.svg")
  override val restoreIcon = restore
  override val restoreInactiveIcon = restore

  val maximize = loadLinuxIcon("window-maximize-symbolic.svg")
  override val maximizeIcon = maximize
  override val maximizeInactiveIcon = maximize

  val minimize = loadLinuxIcon("window-minimize-symbolic.svg")
  override val minimizeIcon = minimize
  override val minimizeInactiveIcon = minimize

  val close = loadLinuxIcon("window-close-symbolic.svg")
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

    println("====================================")
    println("====================================")
    println("====================================")
    println("====================================")
    println(LinuxLookAndFeel.getIconTheme())
    println(LinuxLookAndFeel.getHeaderLayout())
    println("====================================")
    println("====================================")
    println("====================================")
    println("====================================")

    return button
  }


  private fun loadLinuxIcon(iconName: String): Icon {
    /*val iconThemeName = LinuxLookAndFeel.getIconTheme()
    find /usr/share/icons -type f -name "window-maximize-symbolic.svg"
    val themeAdwaita = "Adwaita/scalable/ui/" // Adwaita
    val themeYaru = "Yaru/scalable/ui/" // Yaru
    val themePapirus = "Papirus/symbolic/actions/" // Papirus*/
    val iconPath = findIconAbsolutePath(iconName)
    var icon = IconManager.getInstance().getIcon("file:$iconPath",
                                                 AllIcons::class.java.classLoader)
    icon = IconUtil.colorizeReplace(icon, JBColor(0x6c7080, 0xcfd1d8))
    return icon
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

