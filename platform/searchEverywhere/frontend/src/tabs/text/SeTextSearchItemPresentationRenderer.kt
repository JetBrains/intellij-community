// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.text

import com.intellij.platform.searchEverywhere.SeTextSearchItemPresentation
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultListItemRow
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultListRow
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import javax.swing.ListCellRenderer

@ApiStatus.Internal
class SeTextSearchItemPresentationRenderer {
  fun get(): ListCellRenderer<SeResultListRow> = listCellRenderer {
    val presentation = (value as SeResultListItemRow).item.presentation as SeTextSearchItemPresentation
    val selected = selected
    selectionColor = UIUtil.getListBackground(selected, selected)

    text(presentation.text)

    text(presentation.fileString) {
      align = LcrInitParams.Align.RIGHT
      foreground =
        if (selected) NamedColorUtil.getListSelectionForeground(true)
        else NamedColorUtil.getInactiveTextColor()
    }
  }
}