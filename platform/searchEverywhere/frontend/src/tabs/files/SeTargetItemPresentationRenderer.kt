// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.files

import com.intellij.platform.searchEverywhere.SeTargetItemPresentation
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultListItemRow
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultListRow
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.ListCellRenderer

@Internal
class SeTargetItemPresentationRenderer {
  fun get(): ListCellRenderer<SeResultListRow> = listCellRenderer {
    val presentation = (value as SeResultListItemRow).item.presentation as SeTargetItemPresentation
    val selected = selected
    selectionColor = UIUtil.getListBackground(selected, selected)

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
      @Suppress("HardCodedStringLiteral")
      text(containerText) {
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
}