// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.components


import com.intellij.icons.AllIcons
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JPanel

@ApiStatus.Experimental
enum class ValidationType {
  ERROR, WARNING
}

fun Row.validationTooltip(@Nls message: String,
                          firstActionLink: ActionLink? = null,
                          secondActionLink: ActionLink? = null,
                          validationType: ValidationType = ValidationType.ERROR,
                          inline: Boolean = false): Cell<JPanel> {
return validationTooltip(AtomicProperty(message), firstActionLink, secondActionLink, validationType, inline)
}

@ApiStatus.Experimental
fun Row.validationTooltip(
  textProperty: ObservableMutableProperty<@Nls String>,
  firstActionLink: ActionLink? = null,
  secondActionLink: ActionLink? = null,
  validationType: ValidationType = ValidationType.ERROR,
  inline: Boolean = false,
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
    inline -> UnscaledGaps(top = 8, left = 8, bottom = 8, right = 8)
    hasActionLinks -> UnscaledGaps(top = 10, left = 12, bottom = 12, right = 12)
    else -> UnscaledGaps(top = 8, left = 8, bottom = 8, right = 8)
  }
  return cell(com.intellij.ui.dsl.builder.panel {
    row {
      icon(icon).customize(UnscaledGaps(left = customGaps.left,
                                        top = customGaps.top,
                                        bottom = if (hasActionLinks && !inline) 4 else customGaps.bottom,
                                        right = 8))
      label(textProperty.get())
        .bindText(textProperty)
        .align(Align.FILL)
        .customize(UnscaledGaps(left = 0,
                                top = customGaps.top,
                                bottom = if (hasActionLinks && !inline) 4 else customGaps.bottom,
                                right = customGaps.right))
        .applyToComponent { foreground = foregroundColor }
        .resizableColumn()

      if (inline && hasActionLinks) {
        cell(firstActionLink!!).customize(UnscaledGaps(left = 0,
                                                       top = customGaps.top,
                                                       bottom = customGaps.bottom,
                                                       right = if (secondActionLink == null) 12 else customGaps.right))
        if (secondActionLink != null) {
          cell(secondActionLink).customize(UnscaledGaps(left = customGaps.left,
                                                        top = customGaps.top,
                                                        bottom = customGaps.bottom,
                                                        right = 12))
        }
      }
    }
    if (hasActionLinks && !inline) {
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
  })
    .customize(UnscaledGaps(top = 4, bottom = 4))
    .applyToComponent { border = RoundedLineBorderWithBackground(borderColor, backgroundColor) }
}