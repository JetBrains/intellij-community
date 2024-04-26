// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.dsl

import com.intellij.icons.AllIcons
import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.UIUtil
import java.awt.Color
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.ListCellRenderer

@Suppress("UseJBColor")
internal class ListCellRendererPanel : UISandboxPanel {

  override val title: String = "ListCellRenderer"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      group("JBList") {
        row {
          jbList(listOf("First", "Second", "Last"), textListCellRenderer { it })
          jbList((1..99).map { "Item $it" }, textListCellRenderer { it })
          jbList((1..99).toList(), listCellRenderer {
            icon(if (index % 2 == 0) AllIcons.General.Add else AllIcons.General.Gear)
            text("Item $value")
          }).label("Icons", LabelPosition.TOP)

          val colors = listOf(UIUtil.getLabelForeground(),
                              Color.GREEN,
                              Color.MAGENTA)
          val styles = listOf(SimpleTextAttributes.STYLE_PLAIN,
                              SimpleTextAttributes.STYLE_BOLD,
                              SimpleTextAttributes.STYLE_ITALIC)

          jbList((1..99).toList(), listCellRenderer {
            val i = index % colors.size
            text("Item $value") {
              if (i > 0) {
                foreground = colors[i]
              }
            }
          }).label("Foreground", LabelPosition.TOP)

          jbList((1..99).toList(), listCellRenderer {
            val i = index % colors.size
            text("Item $value") {
              attributes = SimpleTextAttributes(styles[i], colors[i])
            }
          }).label("Attributes", LabelPosition.TOP)

          jbList((1..99).toList(), listCellRenderer {
            val i = index % colors.size
            if (i > 0) {
              background = colors[i]
            }
            text("Item $value")
          }).label("Background", LabelPosition.TOP)

          val aligns = listOf(LcrInitParams.Align.LEFT, LcrInitParams.Align.CENTER, LcrInitParams.Align.RIGHT)
          jbList((1..99).toList(), listCellRenderer {
            val customAlign = aligns.getOrNull(index % (aligns.size + 1))
            text("$value: $customAlign") {
              align = customAlign
            }
          }).label("Align", LabelPosition.TOP)
            .align(Align.FILL)
        }
      }

      group("ComboBox") {
        lateinit var enabled: JCheckBox
        row {
          enabled = checkBox("Enabled")
            .selected(true)
            .component
        }
        indent {
          row("Empty:") {
            comboBox(emptyList<String>(), textListCellRenderer { it })
          }
          row("No selection:") {
            comboBox(listOf("First", "Second", "Last"), textListCellRenderer { it })
              .applyToComponent { selectedItem = null }
          }
          row("Few items:") {
            comboBox(listOf("First", "Second", "Try with y", "Try with ()"), textListCellRenderer { it })
          }
          row("Items with icon:") {
            comboBox((1..100).map { "Item $it" }, listCellRenderer {
              icon(if (index % 2 == 0) AllIcons.General.Add else AllIcons.General.Gear)
              text(value ?: "")
            })
          }
          row("Long items:") {
            comboBox((1..100).map { "$it " + "Item".repeat(10) }, textListCellRenderer { it }).component
          }
        }.enabledIf(enabled.selected)
      }

      group("Renderers") {
        row("iconListCellRenderer:") {
          val renderer = listCellRenderer {
            icon(AllIcons.General.Gear)
            text(value)
          }
          cell(renderer.getListCellRendererComponent(JBList(), "Some text", 0, false, false) as JComponent)
        }

        row("SimpleListCellRenderer:") {
          val renderer = SimpleListCellRenderer.create<String> { label, value, _ ->
            label.icon = AllIcons.General.Gear
            label.text = value
          }
          cell(renderer.getListCellRendererComponent(JBList(), "Some text", 0, false, false) as JComponent)
        }
      }
    }

  }
}

private fun <T> Row.jbList(items: List<T>, renderer: ListCellRenderer<T>): Cell<JBScrollPane> {
  val list = JBList(items)
  list.setCellRenderer(renderer)
  val scroll = JBScrollPane(list)
  scroll.minimumSize = JBDimension(100, 200)
  scroll.isOverlappingScrollBar = true
  return cell(scroll)
}
