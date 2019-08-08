// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.CommonBundle
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.api.data.GHLabel
import java.awt.Color
import java.awt.Component
import java.util.*
import javax.swing.JComponent

object GithubUIUtil {
  val avatarSize = JBUI.uiIntValue("Github.Avatar.Size", 20)

  fun createIssueLabelLabel(label: GHLabel): JBLabel = JBLabel(" ${label.name} ", UIUtil.ComponentStyle.SMALL).apply {
    background = getLabelBackground(label)
    foreground = getLabelForeground(background)
  }.andOpaque()

  fun getLabelBackground(label: GHLabel): JBColor {
    val apiColor = ColorUtil.fromHex(label.color)
    return JBColor(apiColor, ColorUtil.darker(apiColor, 3))
  }

  fun getLabelForeground(bg: Color): Color = if (ColorUtil.isDark(bg)) Color.white else Color.black

  fun setTransparentRecursively(component: Component) {
    if (component is JComponent) {
      component.isOpaque = false
      for (c in component.components) {
        setTransparentRecursively(c)
      }
    }
  }

  fun formatActionDate(date: Date): String {
    val prettyDate = DateFormatUtil.formatPrettyDate(date).toLowerCase()
    val datePrefix = if (prettyDate.equals(CommonBundle.message("date.format.today"), true) ||
                         prettyDate.equals(CommonBundle.message("date.format.yesterday"), true)) ""
    else "on "
    return datePrefix + prettyDate
  }
}