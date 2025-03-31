// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.text

import com.intellij.find.impl.TextSearchListAgnosticRenderer
import com.intellij.find.impl.UsagePresentation
import com.intellij.ide.ui.colors.color
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.platform.searchEverywhere.SeTextSearchItemPresentation
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultListItemRow
import com.intellij.platform.searchEverywhere.frontend.ui.SeResultListRow
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.ui.popup.list.SelectablePanel.Companion.wrap
import com.intellij.usages.TextChunk
import com.intellij.util.containers.toArray
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

@ApiStatus.Internal
class SeTextSearchItemPresentationRenderer {
  fun get(): ListCellRenderer<SeResultListRow> = object : ListCellRenderer<SeResultListRow> {
    private val textSearchRenderer = TextSearchListAgnosticRenderer { list, index ->
      if (index <= 0) null
      else ((list.getModel().getElementAt(index - 1) as? SeResultListItemRow)?.item?.presentation as? SeTextSearchItemPresentation)?.asUsagePresentation()
      null
    }

    private fun SeTextSearchItemPresentation.asUsagePresentation(): UsagePresentation =
      UsagePresentation(textChunks.map { chunk ->
        TextChunk(TextAttributes().apply {
          foregroundColor = chunk.foregroundColorId?.color()
          fontType = chunk.fontType
        }, chunk.text)
      }.toArray(emptyArray()), backgroundColor, fileString)

    override fun getListCellRendererComponent(
      list: JList<out SeResultListRow>?,
      value: SeResultListRow,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean,
    ): Component {
      val textSearchPresentation = ((value as? SeResultListItemRow)?.item?.presentation as? SeTextSearchItemPresentation) ?: return EMPTY_PANEL
      val usagePresentation = textSearchPresentation.asUsagePresentation()

      return wrapIntoSelectablePanel(
        textSearchRenderer.getListCellRendererComponent(list, usagePresentation, index, isSelected, cellHasFocus),
        isSelected,
        textSearchPresentation.backgroundColor, textSearchPresentation.text
      )
    }

    private var lastSavedSelectablePanel: SelectablePanel? = null

    private fun wrapIntoSelectablePanel(component: Component, isSelected: Boolean, unselectedBackground: Color?, text: String): Component {
      var component = component

      if (isNewUI()) {
        val rowBackground: Color? = unselectedBackground.takeIf { it != UIUtil.getListBackground() }
                                    ?: JBUI.CurrentTheme.Popup.BACKGROUND

        var selectablePanel: SelectablePanel? = lastSavedSelectablePanel

        if (component is SelectablePanel) {
          selectablePanel = component
        }
        else {
          if (component is JComponent) {
            component.setBorder(JBUI.Borders.empty())
          }
          UIUtil.setOpaqueRecursively(component, false)
          if (selectablePanel?.components?.firstOrNull() !== component) {
            selectablePanel = wrap(component)
          }
          component.revalidate()
          component = selectablePanel
        }

        selectablePanel.background = rowBackground
        selectablePanel.selectionColor = if (isSelected) UIUtil.getListBackground(true, true) else null
        PopupUtil.configListRendererFixedHeight(selectablePanel)

        lastSavedSelectablePanel = selectablePanel
      }
      else {
        component.setPreferredSize(UIUtil.updateListRowHeight(component.preferredSize))
      }

      return component
    }
  }

  companion object {
    private val EMPTY_PANEL = JPanel()
  }
}