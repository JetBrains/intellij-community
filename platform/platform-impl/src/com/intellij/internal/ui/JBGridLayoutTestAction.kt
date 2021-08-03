// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.gridLayout.*
import com.intellij.ui.gridLayout.builders.RowsGridBuilder
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.*
import javax.swing.border.Border
import kotlin.random.Random


class JBGridLayoutTestAction : DumbAwareAction("Show JBGridLayout Demo") {

  override fun actionPerformed(e: AnActionEvent) {
    object : DialogWrapper(e.project, null, true, IdeModalityType.IDE, false) {
      init {
        title = "UI DSL Showcase"
        init()
      }

      override fun createContentPaneBorder(): Border? {
        return null
      }

      override fun createCenterPanel(): JComponent {
        val result = JBTabbedPane()
        result.minimumSize = Dimension(300, 200)
        result.preferredSize = Dimension(800, 600)
        result.addTab("TODO", createTodoPanel())
        result.addTab(
          "NoResizableCells", createTabPanel("No resizable cells",
                                             createPanelLabels(3, 4) { _, _, _ -> null })
        )
        result.addTab("ResizableCell[1, 1]", createResizableCell11Panel())
        result.addTab("CellAlignments", createCellAlignmentsPanel())
        result.addTab("SubGrid", createSubGridPanel())
        result.addTab("JointCells", createJointCellsPanel())
        result.addTab("Gaps", createGapsPanel())
        result.addTab("Col/row distance", createColRowDistancePanel())
        result.addTab("VisualPaddings", createVisualPaddingsPanel())

        return result
      }
    }.show()
  }
}

fun createTodoPanel(): JPanel {
  val result = JPanel()
  val todo = listOf(
    "Implement cells which occupies all remaining columns",
    "Resize non resizable cells when there is no enough space",
    "Tests",
    "visualPaddings can depend on component size? E.g. checkBox",
    "SubGrids: visibility, visualPaddings"
  )
  result.add(
    JLabel("<html>TODO list<br><br>&bull " + todo.joinToString("<br>&bull "))
  )
  return result
}

fun createVisualPaddingsPanel(): JPanel {
  val layoutManager = JBGridLayout()
  val rootGrid = layoutManager.rootGrid
  rootGrid.resizableColumns = setOf(1)
  rootGrid.resizableRows = setOf(2)
  val panel = JPanel(layoutManager)

  fillGridByLabels(panel, rootGrid, 3, 4) { grid, x, y ->
    if (x == 0 && y == 1) {
      JBConstraints(grid, x, y, visualPaddings = Gaps(10, 10, 10, 10))
    }
    else if (x == 1 && y == 2) {
      JBConstraints(
        grid, x, y, horizontalAlign = HorizontalAlign.FILL,
        verticalAlign = VerticalAlign.FILL,
        visualPaddings = Gaps(10, 10, 10, 10)
      )
    }
    else {
      null
    }
  }

  return createTabPanel("Every second cell has own Gaps", panel)
}

fun createGapsPanel(): JPanel {
  val panel = createPanelLabels(4, 4) { grid, x, y ->
    JBConstraints(
      grid,
      x,
      y,
      horizontalAlign = HorizontalAlign.FILL,
      verticalAlign = VerticalAlign.FILL,
      gaps = if ((x + y) % 2 == 0) Gaps.EMPTY else Gaps(y * 20, x * 20, y * 30, x * 30)
    )
  }
  val grid = (panel.layout as JBGridLayout).rootGrid
  grid.resizableColumns = (0..HorizontalAlign.values().size).toSet()
  grid.resizableRows = (0..VerticalAlign.values().size).toSet()
  return createTabPanel("Every second cell has own Gaps", panel)
}

fun createColRowDistancePanel(): JPanel {
  val layoutManager = JBGridLayout()
  val grid = layoutManager.rootGrid
  grid.resizableColumns = (0..4).toSet()
  grid.resizableRows = (0..3).toSet()
  grid.columnsDistance = listOf(20, 40, 60, 80, 100) // last should be ignored by layout
  grid.rowsDistance = listOf(20, 40, 60, 80)
  val panel = JPanel(layoutManager)

  fillGridByCompoundLabels(panel, grid)

  return createTabPanel("Different distances between columns/rows", panel)
}

fun createJointCellsPanel(): JPanel {
  val layoutManager = JBGridLayout()
  val grid = layoutManager.rootGrid
  grid.resizableColumns = setOf(1)
  grid.resizableRows = setOf(1)
  val panel = JPanel(layoutManager)

  fun addLabel(x: Int, y: Int, width: Int = 1, height: Int = 1) {
    panel.addLabel(
      JBConstraints(
        grid, x, y, width = width, height = height,
        horizontalAlign = HorizontalAlign.FILL, verticalAlign = VerticalAlign.FILL
      )
    )
  }

  addLabel(0, 0, height = 2)
  addLabel(1, 0, width = 3)
  addLabel(4, 0, height = 3)
  addLabel(1, 1)
  val jbConstraints = JBConstraints(
    grid, 2, 1, width = 2, height = 2,
    horizontalAlign = HorizontalAlign.FILL, verticalAlign = VerticalAlign.FILL
  )
  panel.add(
    JLabel(
      "<html>HighLabel<br>Label<br>Label<br>Label<br>Label<br>Label<br>Label<br>${
        constraintsToHtmlString(
          jbConstraints
        )
      }"
    ),
    jbConstraints
  )
  addLabel(0, 2, width = 2, height = 2)
  addLabel(2, 3, width = 3)

  return createTabPanel("Cells have different shapes", panel)
}

fun createCellAlignmentsPanel(): JPanel {
  val panel = createPanelLabels(HorizontalAlign.values().size, HorizontalAlign.values().size) { grid, x, y ->
    JBConstraints(
      grid,
      x,
      y,
      horizontalAlign = HorizontalAlign.values()[x],
      verticalAlign = VerticalAlign.values()[y]
    )
  }
  val grid = (panel.layout as JBGridLayout).rootGrid
  grid.resizableColumns = (0..HorizontalAlign.values().size).toSet()
  grid.resizableRows = (0..VerticalAlign.values().size).toSet()
  return createTabPanel("Cells size is equal, component layouts have different alignments", panel)
}

fun createResizableCell11Panel(): JPanel {
  val panel = createPanelLabels(3, 4) { grid, x, y ->
    if (x == 1 && y == 1)
      JBConstraints(grid, x, y, horizontalAlign = HorizontalAlign.FILL, verticalAlign = VerticalAlign.FILL)
    else
      null
  }
  val grid = (panel.layout as JBGridLayout).rootGrid
  grid.resizableColumns = setOf(1)
  grid.resizableRows = setOf(1)
  return createTabPanel("One column and row are resizable", panel)
}

fun createSubGridPanel(): JPanel {
  val layoutManager = JBGridLayout()
  layoutManager.rootGrid.resizableColumns = setOf(1)
  layoutManager.rootGrid.resizableRows = setOf(1)
  val panel = JPanel(layoutManager)

  val subGrid = layoutManager.addLayoutSubGrid(
    JBConstraints(
      layoutManager.rootGrid, 1, 1,
      horizontalAlign = HorizontalAlign.FILL, verticalAlign = VerticalAlign.FILL
    )
  )
  subGrid.resizableColumns = setOf(1)
  subGrid.resizableRows = setOf(1)
  fillGridByLabels(panel, subGrid, 3, 3) { grid, x, y ->
    if (x == 1 && y == 1)
      JBConstraints(grid, x, y, horizontalAlign = HorizontalAlign.FILL, verticalAlign = VerticalAlign.FILL)
    else
      null
  }

  RowsGridBuilder(panel)
    .cell(label(0, 0))
    .cell(label(1, 0))
    .cell(label(2, 0))
    .row()
    .cell(label(0, 1))
    .skipCell()
    .cell(label(2, 1))
    .row()
    .cell(label(0, 2))
    .cell(label(1, 2))
    .cell(label(2, 2))

  return createTabPanel("cell[1, 1] contains another grid inside", panel)
}

fun createPanelLabels(
  width: Int,
  height: Int,
  constraintFactory: (grid: JBGrid, x: Int, y: Int) -> JBConstraints?
): JPanel {
  val layoutManager = JBGridLayout()
  val result = JPanel(layoutManager)
  fillGridByLabels(result, layoutManager.rootGrid, width, height, constraintFactory)
  return result
}

fun fillGridByLabels(
  container: JComponent,
  grid: JBGrid,
  width: Int,
  height: Int,
  constraintFactory: (grid: JBGrid, x: Int, y: Int) -> JBConstraints?
) {
  for (x in 0 until width) {
    for (y in 0 until height) {
      val constraints =
        constraintFactory.invoke(grid, x, y) ?: JBConstraints(grid, x, y)

      container.addLabel(constraints, longLabel = x == y)
    }
  }
}

fun fillGridByCompoundLabels(
  container: JComponent,
  grid: JBGrid
) {
  fun addLabel(x: Int, y: Int, width: Int = 1, height: Int = 1) {
    container.addLabel(
      JBConstraints(
        grid, x, y, width = width, height = height,
        horizontalAlign = HorizontalAlign.FILL, verticalAlign = VerticalAlign.FILL
      )
    )
  }

  addLabel(0, 0, height = 2)
  addLabel(1, 0, width = 3)
  addLabel(4, 0, height = 3)
  addLabel(1, 1)
  val jbConstraints = JBConstraints(
    grid, 2, 1, width = 2, height = 2,
    horizontalAlign = HorizontalAlign.FILL, verticalAlign = VerticalAlign.FILL
  )
  container.add(
    JLabel(
      "<html>HighLabel<br>Label<br>Label<br>Label<br>Label<br>Label<br>Label<br>${
        constraintsToHtmlString(
          jbConstraints
        )
      }"
    ),
    jbConstraints
  )
  addLabel(0, 2, width = 2, height = 2)
  addLabel(2, 3, width = 3)
}

fun label(constraints: JBConstraints, longLabel: Boolean = false): JLabel {
  val text = if (longLabel) "Very very very very very long label" else "Label"
  return JLabel("<html>$text<br>${constraintsToHtmlString(constraints)}")
}

fun constraintsToHtmlString(constraints: JBConstraints): String {
  var result = "x = ${constraints.x}, y = ${constraints.y}<br>" +
               "width = ${constraints.width}, height = ${constraints.height}<br>" +
               "hAlign = ${constraints.horizontalAlign}, vAlign = ${constraints.verticalAlign}<br>"
  if (constraints.gaps != Gaps.EMPTY) {
    result += "gaps = ${constraints.gaps}<br>"
  }
  if (constraints.visualPaddings != Gaps.EMPTY) {
    result += "visualPaddings = ${constraints.visualPaddings}<br>"
  }
  return result
}

fun gridToHtmlString(grid: JBGrid): String {
  val result = mutableListOf<String>()
  if (grid.resizableColumns.isNotEmpty()) {
    result.add("resizableColumns = ${grid.resizableColumns.joinToString()}")
  }
  if (grid.resizableRows.isNotEmpty()) {
    result.add("resizableRows = ${grid.resizableRows.joinToString()}")
  }
  if (grid.columnsDistance.isNotEmpty()) {
    result.add("columnsDistance = ${grid.columnsDistance.joinToString()}")
  }
  if (grid.rowsDistance.isNotEmpty()) {
    result.add("rowsDistance = ${grid.rowsDistance.joinToString()}")
  }
  return result.joinToString()
}

fun label(x: Int, y: Int, longLabel: Boolean = false): JLabel {
  val text = if (longLabel) "Very very very very very long label" else "Label"
  return JLabel("$text [x = $x, y = $y]")
}

fun JComponent.addLabel(constraints: JBConstraints, longLabel: Boolean = false) {
  val label = label(constraints, longLabel)
  add(label, constraints)
}

fun createTabPanel(title: String, content: JComponent): JPanel {
  val layoutManager = JBGridLayout()
  val rootGrid = layoutManager.rootGrid
  val result = JPanel(layoutManager)
  rootGrid.resizableColumns = setOf(0)
  rootGrid.resizableRows = setOf(1)
  val label = JLabel("<html>$title<br>${gridToHtmlString((content.layout as JBGridLayout).rootGrid)}")
  label.background = Color.LIGHT_GRAY
  label.isOpaque = true
  result.add(label, JBConstraints(rootGrid, 0, 0, width = 2, horizontalAlign = HorizontalAlign.FILL, gaps = Gaps(10)))
  result.add(
    content, JBConstraints(
    rootGrid, 0, 1, verticalAlign = VerticalAlign.FILL,
    horizontalAlign = HorizontalAlign.FILL
  )
  )

  val controlGrid = layoutManager.addLayoutSubGrid(
    JBConstraints(
      rootGrid,
      1,
      1,
      verticalAlign = VerticalAlign.FILL,
      gaps = Gaps(10)
    )
  )
  createControls(result, content, controlGrid)

  return result
}

fun createControls(container: JComponent, content: JComponent, grid: JBGrid) {
  val cbHighlight = JCheckBox("Highlight components")
  cbHighlight.addActionListener {
    for (component in content.components) {
      if (component is JLabel) {
        component.background = if (cbHighlight.isSelected) Color(Random.nextInt()) else null
        component.isOpaque = cbHighlight.isSelected
      }
    }
  }
  cbHighlight.doClick()

  val list = JBList(content.components.filterIsInstance<JLabel>())
  val btnHide = JButton("Hide")
  val btnShow = JButton("Show")
  list.cellRenderer = object : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
      list: JList<*>?,
      value: Any?,
      index: Int,
      isSelected: Boolean,
      cellHasFocus: Boolean
    ): Component {
      val label = value as JLabel
      val result = super.getListCellRendererComponent(
        list,
        label.text,
        index,
        isSelected,
        cellHasFocus
      ) as DefaultListCellRenderer
      result.foreground = if (label.isVisible) Color.BLACK else Color.LIGHT_GRAY

      return result
    }
  }
  btnHide.addActionListener {
    list.selectedValuesList.forEach { it.isVisible = false }
    list.updateUI()
  }
  btnShow.addActionListener {
    list.selectedValuesList.forEach { it.isVisible = true }
    list.updateUI()
  }

  grid.resizableColumns = setOf(0, 1)
  grid.resizableRows = setOf(0)
  container.add(
    JScrollPane(list), JBConstraints(
    grid, 0, 0, width = 2, horizontalAlign = HorizontalAlign.FILL,
    verticalAlign = VerticalAlign.FILL
  )
  )
  container.add(btnHide, JBConstraints(grid, 0, 1, horizontalAlign = HorizontalAlign.CENTER))
  container.add(btnShow, JBConstraints(grid, 1, 1, horizontalAlign = HorizontalAlign.CENTER))
  container.add(cbHighlight, JBConstraints(grid, 0, 2, width = 2))
}
