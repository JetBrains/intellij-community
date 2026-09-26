// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.dsl.listCellRenderer

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.devkit.uiDsl.sandbox.intList
import com.intellij.icons.AllIcons
import com.intellij.internal.inspector.PropertyBean
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.components.Badge
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.selected
import com.intellij.util.ui.JBUI
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.ListCellRenderer

internal class LcrExamplesPanel : UISandboxPanel {

  override val title: String = "Examples"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      lateinit var enabled: JCheckBox

      row {
        enabled = checkBox(DevkitUiDslBundle.message("sandbox.checkbox.enabled"))
          .selected(true)
          .component
      }

      indent {
        row {
          panel {
            val simpleTextLcrExample = createSimpleTextLcrExample()
            row {
              comment(simpleTextLcrExample.comment)
            }
            row {
              comboBox(simpleTextLcrExample.items, simpleTextLcrExample.renderer).applyToComponent {
                selectedIndex = 0
              }.align(AlignX.FILL)
            }
            row {
              scrollCell(JBList(simpleTextLcrExample.items)).applyToComponent {
                cellRenderer = simpleTextLcrExample.renderer
              }.align(Align.FILL)
            }.resizableRow()
          }.align(Align.FILL)

          panel {
            val itemsLcrExample = createItemsLcrExample()
            row {
              comment(itemsLcrExample.comment)
            }
            row {
              comboBox(itemsLcrExample.items, itemsLcrExample.renderer).applyToComponent {
                selectedIndex = 0
              }.align(AlignX.FILL)
            }
            row {
              cell(JBList(itemsLcrExample.items)).applyToComponent {
                cellRenderer = itemsLcrExample.renderer
                border = JBUI.Borders.customLine(JBColor.border())
              }.align(Align.FILL)
            }.resizableRow()
          }.align(Align.FILL)
            .resizableColumn()

          panel {
            val featuresLcrExample = createFeaturesLcrExample()
            row {
              comment(featuresLcrExample.comment)
            }
            row {
              comboBox(featuresLcrExample.items, featuresLcrExample.renderer).applyToComponent {
                selectedIndex = 0
                ComboboxSpeedSearch.installSpeedSearch(this, ::featuresItemToSearchableConverter)
              }.align(AlignX.FILL)
                .label(DevkitUiDslBundle.message("sandbox.label.swing.popup"), LabelPosition.TOP)
            }
            row {
              comboBox(featuresLcrExample.items, featuresLcrExample.renderer).applyToComponent {
                selectedIndex = 0
                isSwingPopup = false
              }.align(AlignX.FILL)
                .label(DevkitUiDslBundle.message("sandbox.label.non.swing.popup"), LabelPosition.TOP)
            }
            row {
              cell(JBList(featuresLcrExample.items)).applyToComponent {
                cellRenderer = featuresLcrExample.renderer
                border = JBUI.Borders.customLine(JBColor.border())
                TreeUIHelper.getInstance().installListSpeedSearch(this, ::featuresItemToSearchableConverter)
              }.align(Align.FILL)
            }.resizableRow()
          }.align(Align.FILL)
            .resizableColumn()
        }
      }.enabledIf(enabled.selected)
    }
  }

  private fun createSimpleTextLcrExample(): LcrExample<Int> {
    @Suppress("DialogTitleCapitalization", "HardCodedStringLiteral")
    return LcrExample(
      comment = "<b>textListCellRenderer</b> maps items<br>to their simple string representation",
      items = intList(),
      renderer = textListCellRenderer { "Item $it" }
    )
  }

  private fun createItemsLcrExample(): LcrExample<Int> {
    @Suppress("DialogTitleCapitalization", "HardCodedStringLiteral")
    return LcrExample(
      comment = "<b>listCellRenderer</b> for complex renderers.<br>All possible items",
      items = intList(11),
      renderer = listCellRenderer {
        when (value) {
          1 -> {
            separator {
              text = "Text"
            }
            text(DevkitUiDslBundle.message("sandbox.normal.text"))
          }
          2 -> {
            text(DevkitUiDslBundle.message("sandbox.red.text")) {
              foreground = JBColor.RED
            }
          }
          3 -> text(DevkitUiDslBundle.message("sandbox.italic")) {
            attributes = SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES
          }
          4 -> text(DevkitUiDslBundle.message("sandbox.bold")) {
            attributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
          }
          5 -> {
            text(DevkitUiDslBundle.message("sandbox.with.comment")) {
              align = LcrInitParams.Align.LEFT
            }
            text(DevkitUiDslBundle.message("sandbox.comment")) {
              foreground = greyForeground
            }
          }
          6 -> {
            text(DevkitUiDslBundle.message("sandbox.with.small.comment")) {
              align = LcrInitParams.Align.LEFT
            }
            text(DevkitUiDslBundle.message("sandbox.comment")) {
              attributes = SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES
            }
          }

          7 -> {
            separator {
              text = "Icon"
            }
            icon(AllIcons.Empty)
            text(DevkitUiDslBundle.message("sandbox.with.reserved.space.for.icon"))
          }
          8 -> {
            icon(AllIcons.General.Information)
            text(DevkitUiDslBundle.message("sandbox.with.icon"))
          }
          9 -> {
            text(DevkitUiDslBundle.message("sandbox.badge.usage"))
            icon(Badge.beta)
          }

          10 -> {
            separator {
              text = "Switch"
            }
            text(DevkitUiDslBundle.message("sandbox.switch.is.off"))
            switch(false) {
              align = LcrInitParams.Align.RIGHT
            }
          }
          11 -> {
            text(DevkitUiDslBundle.message("sandbox.switch.is.on"))
            switch(true) {
              align = LcrInitParams.Align.RIGHT
            }
          }
        }
      }
    )
  }

  @NlsSafe private val SEARCHABLE_COMMENT = "Searchable comment"

  private fun featuresItemToSearchableConverter(s: String): String {
    return when (s) {
      "Normal text 2" -> "$s $SEARCHABLE_COMMENT"
      else -> s
    }
  }

  private fun createFeaturesLcrExample(): LcrExample<String> {
    val items = listOf(
      "Normal text",
      "Normal text 2",
      "Normal text 3",
      "Default bg",
      "Yellow bg",
      "Pink bg",
      "Tooltip",
      "UI Inspector"
    )

    @Suppress("DialogTitleCapitalization", "HardCodedStringLiteral")
    return LcrExample(
      comment = "<b>listCellRenderer</b> features<br>Speed search in ComboBox-es/List",
      items = items,
      renderer = listCellRenderer("") {
        when (value) {
          items[0] -> {
            separator {
              text = "Text"
            }
            @Suppress("HardCodedStringLiteral")
            text(value) {
              speedSearch { }
            }
          }
          items[1] -> {
            @Suppress("HardCodedStringLiteral")
            text(value) {
              align = LcrInitParams.Align.LEFT
              speedSearch { }
            }
            text(SEARCHABLE_COMMENT) {
              speedSearch { }
              foreground = greyForeground
            }
            toolTipText = "The row is searchable by both the text and the comment"
            icon(AllIcons.General.ContextHelp)
          }
          items[2] -> {
            @Suppress("HardCodedStringLiteral")
            text(value) {
              align = LcrInitParams.Align.LEFT
              speedSearch { }
            }
            text(DevkitUiDslBundle.message("sandbox.non.searchable.comment")) {
              foreground = greyForeground
            }
            toolTipText = "The row is searchable by the main text only"
            icon(AllIcons.General.ContextHelp)
          }

          items[3] -> {
            separator {
              text = "Background"
            }

            @Suppress("HardCodedStringLiteral")
            text(value) {
              speedSearch { }
            }
          }
          items[4] -> {
            background = JBColor.YELLOW
            @Suppress("HardCodedStringLiteral")
            text(value) {
              speedSearch { }
            }
          }
          items[5] -> {
            background = JBColor.PINK
            @Suppress("HardCodedStringLiteral")
            text(value) {
              speedSearch { }
            }
          }

          items[6] -> {
            separator {
              text = "Other"
            }

            toolTipText = "Tooltip for the 'Tooltip' row"
            @Suppress("HardCodedStringLiteral")
            text(value)
            icon(AllIcons.General.ContextHelp)
          }
          items[7] -> {
            toolTipText = "Use the UI Inspector to see additional item properties"
            @Suppress("HardCodedStringLiteral")
            text(value)
            icon(AllIcons.General.ContextHelp)
            uiInspectorContext = listOf(PropertyBean("Item id", value), PropertyBean("Item source", "UI Sandbox"))
          }
        }
      }
    )
  }
}

private data class LcrExample<T>(
  val comment: @NlsContexts.DetailedDescription String,
  val items: List<T>,
  val renderer: ListCellRenderer<T?>,
)
