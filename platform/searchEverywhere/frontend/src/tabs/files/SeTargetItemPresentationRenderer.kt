// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.files

import com.intellij.ide.actions.SETextShortener
import com.intellij.platform.searchEverywhere.SeTargetItemPresentation
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultListItemRow
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultListRow
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.JList
import javax.swing.ListCellRenderer

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
      val shortenContainerText = SETextShortener.getShortenContainerText(containerText, width - presentableTextWidth - JBUI.scale(16) - locationTextWidth - JBUI.scale(20), { fontMetrics.stringWidth(it) })

      text(shortenContainerText) {
        attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES

        if (selected) {
          val prefixBoundary = shortenContainerText.commonPrefixWith(containerText).length - 1
          val suffixBoundary = containerText.length - shortenContainerText.commonSuffixWith(containerText).length + 1
          val postSuffixShiftAmount = containerText.length - shortenContainerText.length

          speedSearch {
            ranges = presentation.containerTextMatchedRanges?.map { it.textRange }?.filter { range ->
              range.endOffset <= prefixBoundary || range.startOffset >= suffixBoundary
            }?.map { range ->
              if (range.startOffset >= suffixBoundary) {
                range.shiftLeft(postSuffixShiftAmount)
              }
              else {
                range
              }
            }
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
}