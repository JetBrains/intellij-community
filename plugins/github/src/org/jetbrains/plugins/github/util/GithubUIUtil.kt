// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GithubIssueLabel
import java.awt.Color
import javax.swing.JList

object GithubUIUtil {
   fun createIssueLabelLabel(label: GHLabel): JBLabel = JBLabel(" ${label.name} ", UIUtil.ComponentStyle.SMALL).apply {
    val apiColor = ColorUtil.fromHex(label.color)
    background = JBColor(apiColor, ColorUtil.darker(apiColor, 3))
    foreground = computeForeground(background)
  }.andOpaque()

  private fun computeForeground(bg: Color) = if (ColorUtil.isDark(bg)) Color.white else Color.black
}