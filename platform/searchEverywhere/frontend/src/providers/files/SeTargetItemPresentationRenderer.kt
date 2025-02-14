// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.files

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
  fun get(patternProvider: () -> String): ListCellRenderer<SeResultListRow> = listCellRenderer {
    val presentation = ((value as SeResultListItemRow).item.presentation as SeTargetItemPresentation).targetPresentation()
    val pattern = patternProvider()
    val selected = selected
    selectionColor = UIUtil.getListBackground(selected, selected)

    presentation.icon?.let { icon(it) }
    text(presentation.presentableText)
    presentation.containerText?.let {
      text(it) {
        attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
      }
    }

    // location
    presentation.locationText?.let { locationText ->
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