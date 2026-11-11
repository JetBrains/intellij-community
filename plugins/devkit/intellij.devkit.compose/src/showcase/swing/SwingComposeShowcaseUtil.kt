// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral")

package com.intellij.devkit.compose.showcase.swing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.intellij.platform.compose.swing.components.ComboBox
import com.intellij.platform.compose.swing.components.Comment
import com.intellij.platform.compose.swing.components.SegmentedButton
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.style.horizontalAlignment
import org.jetbrains.compose.swing.foundation.graphics.RoundedCornerShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.RowScope
import org.jetbrains.compose.swing.foundation.layout.fillMaxHeight
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.width
import org.jetbrains.compose.swing.foundation.layout.widthIn
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.border
import org.jetbrains.compose.swing.style.foreground
import org.jetbrains.compose.swing.style.opaque
import org.jetbrains.compose.swing.style.styleable
import java.awt.Color
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.border.Border
import javax.swing.border.CompoundBorder

internal val spacing = IntelliJSpacingConfiguration()

internal val Int.dp: Int get() = JBUI.scale(this)

/** One page of [ShowcasePages]: a [title] in the page list, and a [description] above the [content]. */
internal class ShowcasePage(val title: String, val description: String, val content: @Composable ColumnScope.() -> Unit)

/** A list of the [pages] on the left, and the selected page on the right. The first page shows when the list opens. */
@Composable
internal fun ShowcasePages(pages: List<ShowcasePage>, modifier: SwingModifier = SwingModifier) {
  var selectedPage by remember { mutableIntStateOf(0) }
  val titles = remember(pages) { pages.map { it.title } }
  // The row paints the panel background, so the page list looks the same in every tab, whatever the tab paints behind it.
  Row(
    modifier.styleable {
      background(UIUtil.getPanelBackground())
      opaque(true)
    },
    horizontalArrangement = Arrangement.spacedBy(spacing.horizontalSmallGap.dp),
  ) {
    // The list background sets the list apart from the page, so the scroll pane has no border.
    ScrollPane(SwingModifier.fillMaxHeight(), style = { border(JBUI.Borders.empty()) }) {
      Viewport {
        ListBox(
          items = titles,
          // The child of a scroll pane takes no padding, so an empty border keeps the first and the last item off the edges.
          style = { border(JBUI.Borders.empty(LIST_GAP, 0)) },
          selectedIndices = setOf(selectedPage),
          onSelectionChange = { indices -> indices.firstOrNull()?.let { selectedPage = it } },
          selectionMode = ListSelectionModel.SINGLE_SELECTION,
        ) { title -> PageListItem(title, isSelected) }
      }
    }
    // The pages share one call site, so each page needs its own key. Otherwise a page gets the remembered state of the page before it.
    key(selectedPage) {
      PageContent(pages[selectedPage], SwingModifier.weight(1f).fillMaxHeight())
    }
  }
}

/**
 * One item of the page list. A selected item has a rounded background that stays clear of the list edges. The selection has one
 * color, whether the list has the focus or not.
 */
@Composable
private fun PageListItem(title: String, isSelected: Boolean) {
  // The list paints its own selection background under the item, in a different color with and without the focus. The item paints
  // the list background over the full row, so only the rounded selection shows.
  Box(
    SwingModifier.styleable {
      background(UIUtil.getListBackground())
      opaque(true)
    },
  ) {
    val item = SwingModifier.fillMaxWidth().padding(horizontal = LIST_GAP.dp, vertical = 1.dp)
    val selectionBackground = JBUI.CurrentTheme.List.Selection.background(true)
    Box(if (isSelected) item.background(selectionBackground, RoundedCornerShape(SELECTION_ARC.dp.toFloat())) else item) {
      Label(
        title,
        modifier = SwingModifier.padding(vertical = 4.dp, horizontal = 8.dp),
        style = { foreground(if (isSelected) JBUI.CurrentTheme.List.Selection.foreground(true) else UIUtil.getListForeground()) },
      )
    }
  }
}

/** Shows the description and the content of [page]. The page scrolls vertically when it is too short for the content. */
@Composable
private fun PageContent(page: ShowcasePage, modifier: SwingModifier) {
  // The page has no border, as the page list has none.
  ScrollPane(modifier, style = { border(JBUI.Borders.empty()) }, horizontalScrollbar = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) {
    // The child of a scroll pane takes no padding, so an inner column holds the padding.
    Viewport(unitIncrement = 16.dp) {
      Column {
        Column(
          SwingModifier.fillMaxWidth().padding(all = spacing.verticalSmallGap.dp),
          verticalArrangement = Arrangement.spacedBy(spacing.verticalComponentGap.dp),
        ) {
          // A comment asks for the width of its longest line, which widens the page. With no width, it wraps to the page width.
          Comment(page.description, modifier = SwingModifier.fillMaxWidth().widthIn(max = 0))
          page.content(this)
        }
      }
    }
  }
}

/** Puts the controls of one row side by side, with the gap between related controls, such as a label and its field. */
@Composable
internal fun ColumnScope.ControlsRow(fillWidth: Boolean = true, content: @Composable RowScope.() -> Unit) {
  Row(
    if (fillWidth) SwingModifier.fillMaxWidth() else SwingModifier,
    horizontalArrangement = Arrangement.spacedBy(spacing.horizontalSmallGap.dp),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
  )
}

/** A row with a label of fixed width and the [content] after it. The labels of the rows on one page align. */
@Composable
internal fun ColumnScope.LabeledRow(label: String, content: @Composable RowScope.() -> Unit) {
  ControlsRow {
    Label("$label:", modifier = SwingModifier.width(LABEL_WIDTH.dp))
    content()
  }
}

/**
 * A row with a label and a control that selects one of the [items]. A short list shows as a segmented button, and a long list as a
 * combo box.
 */
@Composable
internal inline fun <reified T : Any> ColumnScope.Selector(
  label: String,
  items: List<T>,
  selectedItem: T,
  noinline onSelectedItemChange: (T) -> Unit,
) {
  LabeledRow(label) {
    if (items.size <= 4) {
      SegmentedButton(
        items = items,
        selectedItem = selectedItem,
        onSelectedItemChange = { if (it != null) onSelectedItemChange(it) },
        renderer = { it.toString() },
      )
    }
    else {
      ComboBox(items = items, selectedItem = selectedItem, onSelectedItemChange = { if (it != null) onSelectedItemChange(it) })
    }
  }
}

/** A label with a border and a background, so the bounds of a layout child are visible. */
@Composable
internal fun DemoCell(text: String, modifier: SwingModifier = SwingModifier, background: Color = CELL_BACKGROUND) {
  Label(
    text,
    modifier = modifier,
    style = {
      border(cellBorder())
      this.background(background)
      opaque(true)
      horizontalAlignment(SwingConstants.CENTER)
    },
  )
}

/** The border of an area that holds the result of a demo. It shows the bounds of the layout. */
internal fun SwingModifier.demoArea(): SwingModifier =
  styleable { border(CompoundBorder(JBUI.Borders.customLine(JBColor.border()), JBUI.Borders.empty(2))) }

private fun cellBorder(): Border = CompoundBorder(JBUI.Borders.customLine(JBColor.border()), JBUI.Borders.empty(4, 8))

private val CELL_BACKGROUND: Color = JBUI.CurrentTheme.Banner.INFO_BACKGROUND

private const val LABEL_WIDTH = 140

private const val LIST_GAP = 6

private const val SELECTION_ARC = 6
