// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.files

import com.intellij.ide.actions.SETextShortener
import com.intellij.ide.rpc.util.textRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.searchEverywhere.SeItemDataKeys
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultListItemRow
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultListRow
import com.intellij.platform.searchEverywhere.frontend.ui.weightTextIfEnabled
import com.intellij.platform.searchEverywhere.presentations.SeTargetItemPresentationImpl
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.impl.LcrRowImpl
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
    val presentation = (value as SeResultListItemRow).item.presentation as SeTargetItemPresentationImpl
    val selected = selected
    selectionColor = UIUtil.getListBackground(selected, selected)
    presentation.backgroundColor?.let { background = it }

    if (Registry.`is`("search.everywhere.ml.semantic.highlight.items", false)) {
      val isSemantic = (value as SeResultListItemRow).item.additionalInfo[SeItemDataKeys.IS_SEMANTIC].toBoolean()
      if (isSemantic) {
        background = JBColor.GREEN.darker().darker()
      }
    }

    presentation.icon?.let { icon(it) }

    // Calculate widths
    val defaultGapWidth = JBUI.scale(LcrRowImpl.DEFAULT_GAP)
    val bordersWidth = 2 * JBUI.CurrentTheme.Popup.Selection.LEFT_RIGHT_INSET.get() +
                       JBUI.CurrentTheme.Popup.Selection.innerInsets().left + JBUI.CurrentTheme.Popup.Selection.innerInsets().right
    var accumulatedContentWidth = defaultGapWidth + bordersWidth

    presentation.icon?.let {
      accumulatedContentWidth += (JBUI.scale(it.iconWidth) + defaultGapWidth)
    }

    val fontMetrics = resultList.getFontMetrics(resultList.font)
    val presentableTextWidth = fontMetrics.stringWidth(presentation.presentableText)
    // Calculate the combined width without locationText.
    // If it is larger than the available space, we need to hide the locationIcon to avoid text overlap (IJPL-188565).
    accumulatedContentWidth += (presentableTextWidth + defaultGapWidth)

    text(presentation.presentableText) {
      accessibleName = presentation.presentableText + (presentation.containerText?.let { " $it" } ?: "")

      if (presentation.presentableTextStrikethrough) {
        attributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, presentation.presentableTextFgColor)
      }
      else if (presentation.presentableTextErrorHighlight) {
        attributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED,
                                          presentation.presentableTextFgColor,
                                          JBColor.RED)
      }
      else {
        presentation.presentableTextFgColor?.let { foreground = it }
      }

      if (selected) {
        speedSearch {
          ranges = presentation.presentableTextMatchedRanges?.map { it.textRange() }
        }
      }
    }

    weightTextIfEnabled(value)
    val locationTextWidth = presentation.locationText?.let { fontMetrics.stringWidth(it) + defaultGapWidth } ?: 0

    presentation.containerText?.takeIf {
      accumulatedContentWidth + locationTextWidth < resultList.width
    }?.let { containerText ->
      val width = resultList.width
      val maxContainerTextWidth = width - accumulatedContentWidth - locationTextWidth
      val shortenContainerText = SETextShortener.getShortenContainerText(containerText, maxContainerTextWidth, { fontMetrics.stringWidth(it) })
      accumulatedContentWidth += fontMetrics.stringWidth(shortenContainerText)

      text(shortenContainerText) {
        accessibleName = null
        attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES

        if (selected) {
          val prefixBoundary = shortenContainerText.commonPrefixWith(containerText).length - 1
          val suffixBoundary = containerText.length - shortenContainerText.commonSuffixWith(containerText).length + 1
          val postSuffixShiftAmount = containerText.length - shortenContainerText.length

          speedSearch {
            ranges = presentation.containerTextMatchedRanges?.map { it.textRange() }?.filter { range ->
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

    presentation.locationText?.let { locationText ->
      @Suppress("HardCodedStringLiteral")
      text(locationText) {
        accessibleName = null
        align = LcrInitParams.Align.RIGHT
        foreground =
          if (selected) NamedColorUtil.getListSelectionForeground(true)
          else NamedColorUtil.getInactiveTextColor()
      }

      accumulatedContentWidth += locationTextWidth

      presentation.locationIcon?.let { locationIcon ->
        if ((accumulatedContentWidth + JBUI.scale(locationIcon.iconWidth) + defaultGapWidth) < resultList.width) {
          icon(locationIcon)
        }
      }
    }
  }
}