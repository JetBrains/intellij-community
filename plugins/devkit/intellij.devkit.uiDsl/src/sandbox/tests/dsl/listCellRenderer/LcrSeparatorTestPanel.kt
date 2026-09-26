// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DialogTitleCapitalization")

package com.intellij.devkit.uiDsl.sandbox.tests.dsl.listCellRenderer

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.devkit.uiDsl.sandbox.jbList
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.ListItemDescriptor
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.ui.GroupedComboBoxRenderer
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.popup.list.GroupedItemsListRenderer
import com.intellij.util.text.nullize
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.ListCellRenderer

private val items: List<String> = mutableListOf("The first group", "Item 1", "Item 2",
                                                "Another Item 1", "Another Item 2",
                                                "Group Item 1").apply {
  addAll((2..20).map { "Group Item $it" })
}

private val separators = mapOf("The first group" to "The first", "Another Item 1" to "", "Group Item 1" to "Group")

internal class LcrSeparatorTestPanel : UISandboxPanel {

  override val title: String = "Separator"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        panel {
          group(DevkitUiDslBundle.message("sandbox.border.title.new.api")) {
            row {
              jbList(null, items, newApiRenderer())
                .comment(DevkitUiDslBundle.message("sandbox.text.see.also.tooltips"))
            }
            row {
              @Suppress("HardCodedStringLiteral")
              comboBox(items, newApiRenderer())
                .comment("isSwingPopup = true,<br>tooltips")
                .applyToComponent {
                  isSwingPopup = true
                }
            }
            row {
              @Suppress("HardCodedStringLiteral")
              comboBox(items, newApiRenderer())
                .comment("isSwingPopup = false,<br>speed search enabled,<br>tooltips")
                .applyToComponent {
                  isSwingPopup = false
                }
            }
          }
        }.align(AlignY.TOP)
        panel {
          group(DevkitUiDslBundle.message("sandbox.border.title.old.api")) {
            row {
              jbList(null, items, MyGroupedItemsListRenderer())
            }
            row {
              @Suppress("HardCodedStringLiteral")
              comboBox(items, MyGroupedComboBoxRenderer())
                .comment("isSwingPopup = true")
                .applyToComponent {
                  isSwingPopup = true
                }
            }
            row {
              @Suppress("HardCodedStringLiteral")
              comboBox(items, MyGroupedComboBoxRenderer())
                .comment("isSwingPopup = false,<br>speed search enabled")
                .applyToComponent {
                  isSwingPopup = false
                }
            }
          }
        }.align(AlignY.TOP)
      }
    }
  }

  private fun newApiRenderer(): ListCellRenderer<String?> {
    return listCellRenderer("") {
      toolTipText = value
      separators[value]?.let {
        separator {
          text = it
        }
      }
      @Suppress("HardCodedStringLiteral")
      text(value)
    }
  }

  private class MyGroupedItemsListRenderer :
    GroupedItemsListRenderer<String>(MyListItemDescriptor(separators))

  private class MyListItemDescriptor(private val separators: Map<String, String>) : ListItemDescriptor<String> {
    @Suppress("HardCodedStringLiteral")
    override fun getTextFor(value: String?): String? = value

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

  private class MyGroupedComboBoxRenderer : GroupedComboBoxRenderer<String?>() {
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