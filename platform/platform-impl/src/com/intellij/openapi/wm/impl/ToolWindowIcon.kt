// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
import com.intellij.ui.IconReplacer
import com.intellij.ui.RetrievableIcon
import com.intellij.ui.icons.MenuBarIconProvider
import com.intellij.util.IconUtil
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

/**
 * @author Konstantin Bulenkov
 */
internal class ToolWindowIcon(private val icon: Icon,
                              private val toolWindowId: String) : RetrievableIcon, MenuBarIconProvider, ScalableIcon {

  override fun replaceBy(replacer: IconReplacer): ToolWindowIcon {
    return ToolWindowIcon(replacer.replaceIcon(icon), toolWindowId)
  }

  override fun retrieveIcon() = icon

  override fun getMenuBarIcon(isDark: Boolean) = ToolWindowIcon(IconLoader.getMenuBarIcon(icon, isDark), toolWindowId)

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    icon.paintIcon(c, g, x, y)
  }

  override fun getIconWidth() = icon.iconWidth

  override fun getIconHeight() = icon.iconHeight

  override fun getScale() = if (icon is ScalableIcon) icon.scale else 1f

  override fun scale(scaleFactor: Float) = ToolWindowIcon(IconUtil.scaleOrLoadCustomVersion(icon, scaleFactor), toolWindowId)
}