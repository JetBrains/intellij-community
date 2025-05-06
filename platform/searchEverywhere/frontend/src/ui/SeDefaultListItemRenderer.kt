// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.ide.ui.colors.color
import com.intellij.ide.ui.icons.icon
import com.intellij.platform.searchEverywhere.SeSimpleItemPresentation
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import javax.swing.ListCellRenderer

@ApiStatus.Internal
class SeDefaultListItemRenderer {
  fun get(): ListCellRenderer<SeResultListRow> = listCellRenderer {
    selectionColor = UIUtil.getListBackground(selected, selected)

    when (val value = value) {
      is SeResultListItemRow -> {
        when (val presentation = value.item.presentation) {
          is SeSimpleItemPresentation -> {
            presentation.iconId?.icon()?.let { icon(it) }

            if (selected) {
              (presentation.selectedTextChunk ?: presentation.textChunk)?.let { selectedTextChunk ->
                text(selectedTextChunk.text) {
                  attributes = SimpleTextAttributes(selectedTextChunk.fontType, selectedTextChunk.foregroundColorId?.color())
                }
              }
            }
            else {
              presentation.textChunk?.let { textChunk ->
                text(textChunk.text) {
                  attributes = SimpleTextAttributes(textChunk.fontType, textChunk.foregroundColorId?.color())
                }
              }
            }

            presentation.description?.let {
              text(it) {
                align = LcrInitParams.Align.RIGHT
                attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
              }
            }
          }
          else ->  throw IllegalStateException("Item is not handled: $presentation")
        }
      }
      is SeResultListMoreRow -> icon(AnimatedIcon.Default.INSTANCE)
    }
  }
}