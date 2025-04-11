// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.files

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.searchEverywhere.SeTargetItemPresentation
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultListItemRow
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultListRow
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.FontMetrics
import java.util.*
import javax.swing.JList
import javax.swing.ListCellRenderer
import kotlin.math.max

@Internal
class SeTargetItemPresentationRenderer(private val resultList: JList<SeResultListRow>) {
  fun get(): ListCellRenderer<SeResultListRow> = listCellRenderer {
    val presentation = (value as SeResultListItemRow).item.presentation as SeTargetItemPresentation
    val selected = selected
    selectionColor = UIUtil.getListBackground(selected, selected)
    presentation.backgroundColor?.let { background = it }

    presentation.icon?.let { icon(it) }

    text(presentation.presentableText) {
      presentation.presentableTextFgColor?.let { foreground = it }
      if (selected) {
        speedSearch {
          ranges = presentation.presentableTextMatchedRanges?.map { it.textRange }
        }
      }
    }

    presentation.containerText?.let { containerText ->
      val fontMetrics = resultList.getFontMetrics(resultList.font)
      val presentableTextWidth = fontMetrics.stringWidth(presentation.presentableText)
      val locationTextWidth = presentation.locationText?.let { fontMetrics.stringWidth(it) } ?: 0

      val width = resultList.width

      @Suppress("HardCodedStringLiteral")
      text(getContainerTextForLeftComponent(containerText, width - presentableTextWidth - 16 - locationTextWidth - 20, fontMetrics)) {
        attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES

        if (selected) {
          speedSearch {
            ranges = presentation.containerTextMatchedRanges?.map { it.textRange }
          }
        }
      }
    }

    // location
    presentation.locationText?.let { locationText ->
      @Suppress("HardCodedStringLiteral")
      text(locationText) {
        align = LcrInitParams.Align.RIGHT
        foreground =
          if (selected) NamedColorUtil.getListSelectionForeground(true)
          else NamedColorUtil.getInactiveTextColor()
      }

      presentation.locationIcon?.let { locationIcon ->
        icon(locationIcon)
      }
    }
  }

  private fun getContainerTextForLeftComponent(containerText: String, maxWidth: Int, fm: FontMetrics): String {
    var text = containerText
    val textStartsWithIn = text.startsWith("in ")
    if (textStartsWithIn) text = text.substring(3)
    val left = if (textStartsWithIn) "in " else ""
    val adjustedText = left + text
    if (maxWidth < 0) return adjustedText

    val fullWidth = fm.stringWidth(adjustedText)
    if (fullWidth < maxWidth) return adjustedText

    val separator = if (text.contains("/")) "/" else if (SystemInfo.isWindows && text.contains("\\")) "\\" else if (text.contains(".")) "." else if (text.contains("-")) "-" else " "
    val parts = LinkedList<String?>(StringUtil.split(text, separator))
    var index: Int
    while (parts.size > 1) {
      index = parts.size / 2 - 1
      parts.removeAt(index)
      if (fm.stringWidth(left + StringUtil.join(parts, separator) + "...") < maxWidth) {
        parts.add(index, "...")
        return left + StringUtil.join(parts, separator)
      }
    }
    val adjustedWidth = max((adjustedText.length * maxWidth / fullWidth - 1).toDouble(), (left.length + 3).toDouble()).toInt()
    return StringUtil.trimMiddle(adjustedText, adjustedWidth)
  }
}