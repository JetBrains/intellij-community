// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.dsl.listCellRenderer

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.ListItemDescriptor
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.ui.GroupedComboBoxRenderer
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.popup.list.GroupedItemsListRenderer
import com.intellij.util.text.nullize
import javax.swing.Icon
import javax.swing.JComponent

internal class LcrSeparatorPanel : UISandboxPanel {

  override val title: String = "Separator"

  override fun createContent(disposable: Disposable): JComponent {
    val items = listOf("Item 1", "Item 2",
                       "Another Item 1", "Another Item 2",
                       "Group Item 1", "Group Item 2")
    val separators = mapOf("Another Item 1" to "", "Group Item 1" to "Group")
    return panel {
      group("Old API") {
        row {
          jbList(items, MyGroupedItemsListRenderer(separators))
        }
        row {
          comboBox(items, MyGroupedComboBoxRenderer(separators)).applyToComponent {
            isSwingPopup = false
          }
        }
      }
    }
  }

  private class MyGroupedItemsListRenderer(separators: Map<String, String>) :
    GroupedItemsListRenderer<String>(MyListItemDescriptor(separators)) {
  }

  private class MyListItemDescriptor(private val separators: Map<String, String>) : ListItemDescriptor<String> {
    override fun getTextFor(value: String?): String? {
      return value
    }

    override fun getTooltipFor(value: String?): String? {
      return null
    }

    override fun getIconFor(value: String?): Icon? {
      return null
    }

    override fun hasSeparatorAboveOf(value: String?): Boolean {
      return separators.containsKey(value)
    }

    override fun getCaptionAboveOf(value: String?): String? {
      return separators[value].nullize()
    }
  }

  private class MyGroupedComboBoxRenderer(private val separators: Map<String, String>) : GroupedComboBoxRenderer<String?>() {
    override fun separatorFor(value: String?): ListSeparator? {
      val title = separators[value]
      return if (title == null) {
        null
      }
      else {
        ListSeparator(title)
      }
    }

    override fun getText(item: String?): String {
      return item ?: ""
    }
  }
}