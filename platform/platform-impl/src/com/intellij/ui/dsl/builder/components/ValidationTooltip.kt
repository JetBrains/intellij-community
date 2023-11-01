// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.components


import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.*
import javax.swing.JPanel
import javax.swing.border.LineBorder

@ApiStatus.Experimental
enum class ValidationType {
  ERROR, WARNING
}

private class RoundedLineBorder(color: Color,
                                private val bgColor: Color,
                                private val arcSize: Int = JBUI.scale(10),
                                thickness: Int = JBUI.scale(1)) : LineBorder(color, thickness) {

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val g2 = g as Graphics2D

    val oldAntialiasing = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    val oldColor = g2.color

    g2.color = bgColor
    g2.fillRoundRect(x + thickness - 1, y + thickness - 1,
                     width - thickness - thickness + 1,
                     height - thickness - thickness + 1,
                     arcSize, arcSize)

    g2.color = lineColor
    for (i in 0 until thickness) {
      g2.drawRoundRect(x + i, y + i, width - i - i - 1, height - i - i - 1, arcSize, arcSize)
    }

    g2.color = oldColor
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing)
  }

  override fun getBorderInsets(c: Component, insets: Insets): Insets {
    return Insets(0, 0, 0, 0)
  }
}

@ApiStatus.Experimental
fun Row.validationTooltip(
  validationType: ValidationType,
  @Nls message: String,
  firstActionLink: ActionLink? = null,
  secondActionLink: ActionLink? = null,
) : Cell<JPanel> {
  val hasActionLinks = firstActionLink != null
  val (backgroundColor, foregroundColor, borderColor) = when (validationType) {
    ValidationType.ERROR -> Triple(
      JBColor.namedColor("ValidationTooltip.errorBackground", JBColor.RED),
      JBColor.namedColor("ValidationTooltip.errorForeground", JBColor.BLACK),
      JBColor.namedColor("ValidationTooltip.errorBorderColor", JBColor.RED)
    )
    ValidationType.WARNING -> Triple(
      JBColor.namedColor("ValidationTooltip.warningBackground", JBColor.YELLOW),
      JBColor.namedColor("ValidationTooltip.warningForeground", JBColor.BLACK),
      JBColor.namedColor("ValidationTooltip.warningBorderColor", JBColor.YELLOW)
    )
  }
  val icon = when (validationType) {
    ValidationType.WARNING -> AllIcons.General.Warning
    ValidationType.ERROR -> AllIcons.General.Error
  }

  val customGaps = when {
    hasActionLinks -> UnscaledGaps(top = 10, left = 12, bottom = 12, right = 12)
    else -> UnscaledGaps(top = 8, left = 8, bottom = 8, right = 8)
  }
  return cell(com.intellij.ui.dsl.builder.panel {
    row {
      icon(icon).customize(UnscaledGaps(left = customGaps.left,
                           top = customGaps.top,
                           bottom = if (hasActionLinks) 4 else customGaps.bottom,
                           right = 8))
      label(message)
        .align(Align.FILL)
        .customize(UnscaledGaps(left = 0,
                                top = customGaps.top,
                                bottom = if (hasActionLinks) 4 else customGaps.bottom,
                                right = customGaps.right)).also {
        it.component.foreground = foregroundColor
      }
    }
    if (hasActionLinks) {
      row {
        cell(firstActionLink!!).customize(UnscaledGaps(top = 4,
                                                       left = customGaps.left + 8 + 16, // gap between icon and text + icon width
                                                       bottom = customGaps.bottom,
                                                       right = 16))
          .align(Align.FILL)
        if (secondActionLink != null) {
          cell(secondActionLink).customize(UnscaledGaps(top = 4,
                                                        left = 0,
                                                        bottom = customGaps.bottom,
                                                        right = customGaps.right)).align(Align.FILL)
        }
      }
    }
  }.also {
    it.border = RoundedLineBorder(borderColor, backgroundColor)
  }).customize(UnscaledGaps(top = 4, bottom = 4))
}