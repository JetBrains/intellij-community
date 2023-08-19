// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.frameTitleButtons

import com.intellij.icons.AllIcons
import com.intellij.openapi.wm.impl.customFrameDecorations.LinuxLookAndFeel
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

  val maximize = loadLinuxIcon("window-maximize-symbolic")
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
    //button.preferredSize = Dimension(5, 5)

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
    val themeAdwaita = "Adwaita/scalable/ui/" // Adwaita
    val themeYaru = "Yaru/scalable/ui/" // Yaru
    val themePapirus = "Papirus/symbolic/actions/" // Papirus
    var icon = IconManager.getInstance().getIcon("file:/usr/share/icons/$themePapirus$iconName",
                                                 AllIcons::class.java.classLoader)
    icon = IconUtil.colorizeReplace(icon, JBColor(0x6c7080, 0xcfd1d8))
    return icon
  }
}


private class HoveredCircleButtonUI : BasicButtonUI() {
  private val circleDiameter = 24
  override fun paint(g: Graphics, c: JComponent) {
    g.color = Color(0x434343)
    getHoverColor(c)?.let {
      g.color = Color(0x4e4e4e)
      //g.fillRect(0, 0, c.width, c.height)
    }
    if (g is Graphics2D) {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.fillRoundRect((c.width / 2) - (circleDiameter / 2), (c.height / 2) - (circleDiameter / 2), circleDiameter, circleDiameter, c.width, c.height)
    }
    super.paint(g, c)
  }

  private fun getHoverColor(c: JComponent): Color? = c.getClientProperty(HOVER_KEY) as? Color
}

/*
private class Dconf {

  fun getIconTheme(): String? {
    return getDconfEntry("/org/gnome/desktop/interface/icon-theme")
  }

  fun getHeaderLayout(): String? {
    // Next line returns something like appmenu:minimize,maximize,close
    return getDconfEntry("/org/gnome/desktop/wm/preferences/button-layout")
  }

  private fun getDconfEntry(key: String): String? {
    return execute("dconf read $key")
  }

  private fun execute(command: String): String? {
    val processBuilder = ProcessBuilder(command.split(" "))
    processBuilder.redirectErrorStream(true)

    val process = processBuilder.start()
    val reader = BufferedReader(InputStreamReader(process.inputStream))

    val line: String? = reader.readLine() // Leer la primera línea
    val exitCode = process.waitFor()

    return line
  }

}*/
