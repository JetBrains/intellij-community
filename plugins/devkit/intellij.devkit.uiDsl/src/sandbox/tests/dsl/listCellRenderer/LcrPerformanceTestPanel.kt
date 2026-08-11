// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DialogTitleCapitalization")

package com.intellij.devkit.uiDsl.sandbox.tests.dsl.listCellRenderer

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.ui.ClientProperty
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.WideSelectionListUI
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.VerticalComponentGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.speedSearch.ListWithFilter
import com.intellij.ui.speedSearch.NameFilteringListModel
import com.intellij.util.ReflectionUtil
import org.jetbrains.annotations.Nls
import javax.swing.DefaultListModel
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import kotlin.time.measureTime

internal class LcrPerformanceTestPanel : UISandboxPanel {

  private enum class RendererType {
    KOTLIN_UI_DSL,
    COLORED_LIST_CELL_RENDERER,
  }

  override val title: String = "Performance"

  override fun createContent(disposable: Disposable): JComponent {
    val list: JBList<Int> = JBList(DefaultListModel())
    return panel {
      row {
        cell(ListWithFilter.wrap(list, JBScrollPane(list)) { getMainText(it) })
          .applyToComponent {
            putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap.BOTH)
          }
          .comment(DevkitUiDslBundle.message("sandbox.text.speed.search.supported"))
          .align(Align.FILL)
          .resizableColumn()
        applyRenderer(list, RendererType.KOTLIN_UI_DSL)

        panel {
          row {
            val tfCount = intTextField()
              .text("10000")
              .component
            button(DevkitUiDslBundle.message("sandbox.button.generate")) {
              val model = list.model as NameFilteringListModel<Int>
              val count = tfCount.text.toIntOrNull() ?: return@button
              val size = model.size
              model.addAll((size..<size + count).toList())
              list.invalidate()
            }
          }

          row(DevkitUiDslBundle.message("sandbox.renderer")) {
            comboBox(RendererType.entries)
              .applyToComponent {
                selectedItem = RendererType.KOTLIN_UI_DSL
              }.onChanged {
                applyRenderer(list, it.selectedItem as RendererType)
              }
          }

          row {
            checkBox("ImmutableModelAndRenderer")
              .comment(DevkitUiDslBundle.message("sandbox.text.turn.on.performance.optimizations"))
              .onChanged {
                ClientProperty.put(list, JBList.IMMUTABLE_MODEL_AND_RENDERER, it.isSelected)
              }
          }

          row {
            lateinit var label: JLabel
            button("updateLayoutState") {
              val duration = measureTime {
                val method = ReflectionUtil.getDeclaredMethod(WideSelectionListUI::class.java, "updateLayoutState")
                             ?: throw IllegalStateException("updateLayoutState method not found")
                method.invoke(list.ui)
              }
              label.text = DevkitUiDslBundle.message("sandbox.0.ms", duration.inWholeMilliseconds)
            }
            label = label("").component
          }
        }
      }
    }
  }

  private fun applyRenderer(list: JBList<Int>, renderer: RendererType) {
    list.cellRenderer = when (renderer) {
      RendererType.KOTLIN_UI_DSL -> listCellRenderer("") {
        icon(getMainIcon(value))
        text(getMainText(value)) {
          speedSearch { }
        }
        text(getSecondaryText(value)) {
          foreground = greyForeground
        }
        text(getRightText(value)) {
          foreground = greyForeground
          align = LcrInitParams.Align.RIGHT
        }
        icon(getRightIcon(value))
      }

      RendererType.COLORED_LIST_CELL_RENDERER -> object : ColoredListCellRenderer<Int>() {
        override fun customizeCellRenderer(list: JList<out Int?>, value: Int?, index: Int, selected: Boolean, hasFocus: Boolean) {
          if (value == null) {
            return
          }

          icon = getMainIcon(value)
          append(getMainText(value))
          append(getSecondaryText(value), SimpleTextAttributes.GRAYED_ATTRIBUTES)
          append(getRightText(value))
        }
      }
    }
  }

  private fun getMainIcon(index: Int): Icon {
    return when (index % 3) {
      0 -> AllIcons.Actions.AddToDictionary
      1 -> AllIcons.Nodes.Folder
      else -> AllIcons.Nodes.HomeFolder
    }
  }

  private fun getMainText(index: Int): @Nls String {
    return DevkitUiDslBundle.message("sandbox.some.main.text.item.0", index)
  }

  private fun getSecondaryText(index: Int): @Nls String {
    return DevkitUiDslBundle.message("sandbox.secondary.0", index)
  }

  private fun getRightText(index: Int): @Nls String {
    return DevkitUiDslBundle.message("sandbox.right.0", index)
  }

  private fun getRightIcon(index: Int): Icon {
    return when (index % 4) {
      0 -> AllIcons.Actions.MoveToBottomLeft
      1 -> AllIcons.Actions.MoveToBottomRight
      2 -> AllIcons.Actions.MoveToLeftBottom
      else -> AllIcons.Actions.MoveToLeftTop
    }
  }
}
