// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.gridLayoutTestAction

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.gridLayout.*
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.*
import javax.swing.border.Border
import kotlin.random.Random


class GridLayoutTestAction : DumbAwareAction("Show GridLayout Test") {

  override fun actionPerformed(e: AnActionEvent) {
    object : DialogWrapper(e.project, null, true, IdeModalityType.IDE, false) {
      init {
        title = "GridLayout Test"
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
        result.addTab("Col/row gaps", createColRowGapsPanel())
        result.addTab("VisualPaddings", createVisualPaddingsPanel())
        result.addTab("Baseline", createBaselinePanel())
        result.addTab("SizeGroup", SizeGroupPanel().panel)

        return result
      }
    }.show()
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

  fun createBaselinePanel(): JPanel {
    fun RowsGridBuilder.label(verticalAlign: VerticalAlign, size: Int): RowsGridBuilder {
      val label = JLabel("${verticalAlign.name} $size")
      label.font = label.font.deriveFont(size.toFloat())
      cell(label, verticalAlign = verticalAlign)
      return this
    }

    fun RowsGridBuilder.title(text: String): RowsGridBuilder {
      val label = JLabel(text)
      label.preferredSize = Dimension(150, 40)
      label.verticalAlignment = SwingConstants.TOP
      cell(label, verticalAlign = VerticalAlign.FILL)
      return this
    }

    val panel = JPanel(GridLayout())
    val builder = RowsGridBuilder(panel)
      .defaultBaselineAlign(true)

    builder
      .title("Vertical align: TOP")
      .label(VerticalAlign.TOP, 14)
      .label(VerticalAlign.TOP, 10)
      .label(VerticalAlign.TOP, 16)
      .row()
      .title("Vertical align: CENTER")
      .label(VerticalAlign.CENTER, 12)
      .label(VerticalAlign.CENTER, 14)
      .label(VerticalAlign.CENTER, 16)
      .row()
      .title("Vertical align: BOTTOM")
      .label(VerticalAlign.BOTTOM, 12)
      .label(VerticalAlign.BOTTOM, 10)
      .label(VerticalAlign.BOTTOM, 16)
      .row()
      .title("Vertical align: mixed")
      .label(VerticalAlign.TOP, 12)
      .label(VerticalAlign.CENTER, 10)
      .label(VerticalAlign.BOTTOM, 14)
      .label(VerticalAlign.CENTER, 16)
      .label(VerticalAlign.TOP, 14)
      .label(VerticalAlign.BOTTOM, 10)
      .row()

    builder
      .subGridBuilder(width = 7)
      .title("sub-panels")
      .label(VerticalAlign.CENTER, 14)
      .subGridBuilder(verticalAlign = VerticalAlign.CENTER)
      .label(VerticalAlign.CENTER, 12)
      .subGridBuilder(verticalAlign = VerticalAlign.CENTER)
      .label(VerticalAlign.CENTER, 16)
      .label(VerticalAlign.CENTER, 10)

    return createTabPanel("Labels are aligned by baseline", panel)
  }

  fun createVisualPaddingsPanel(): JPanel {
    val layoutManager = GridLayout()
    val rootGrid = layoutManager.rootGrid
    rootGrid.resizableColumns.add(1)
    rootGrid.resizableRows.add(2)
    val panel = JPanel(layoutManager)

    fillGridByLabels(panel, rootGrid, 3, 4) { grid, x, y ->
      if (x == 0 && y == 1) {
        Constraints(grid, x, y, visualPaddings = Gaps(10, 10, 10, 10))
      }
      else if (x == 1 && y == 2) {
        Constraints(
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
      Constraints(
        grid,
        x,
        y,
        horizontalAlign = HorizontalAlign.FILL,
        verticalAlign = VerticalAlign.FILL,
        gaps = if ((x + y) % 2 == 0) Gaps.EMPTY else Gaps(y * 20, x * 20, y * 30, x * 30)
      )
    }
    val grid = (panel.layout as GridLayout).rootGrid
    grid.resizableColumns.addAll(0..HorizontalAlign.values().size)
    grid.resizableRows.addAll(0..VerticalAlign.values().size)
    return createTabPanel("Every second cell has own Gaps", panel)
  }

  fun createColRowGapsPanel(): JPanel {
    val layoutManager = GridLayout()
    val grid = layoutManager.rootGrid
    grid.resizableColumns.addAll(0..4)
    grid.resizableRows.addAll(0..3)
    grid.columnsGaps.addAll((0..4).map { HorizontalGaps(it * 20, it * 20 + 10) })
    grid.rowsGaps.addAll((0..3).map { VerticalGaps(it * 5 + 5, it * 5 + 15) })
    val panel = JPanel(layoutManager)

    fillGridByCompoundLabels(panel, grid)

    return createTabPanel("Different distances between columns/rows", panel)
  }

  fun createJointCellsPanel(): JPanel {
    val layoutManager = GridLayout()
    val grid = layoutManager.rootGrid
    grid.resizableColumns.add(1)
    grid.resizableRows.add(1)
    val panel = JPanel(layoutManager)

    fun addLabel(x: Int, y: Int, width: Int = 1, height: Int = 1) {
      panel.addLabel(
        Constraints(
          grid, x, y, width = width, height = height,
          horizontalAlign = HorizontalAlign.FILL, verticalAlign = VerticalAlign.FILL
        )
      )
    }

    addLabel(0, 0, height = 2)
    addLabel(1, 0, width = 3)
    addLabel(4, 0, height = 3)
    addLabel(1, 1)
    val constraints = Constraints(
      grid, 2, 1, width = 2, height = 2,
      horizontalAlign = HorizontalAlign.FILL, verticalAlign = VerticalAlign.FILL
    )
    panel.add(
      JLabel(
        "<html>HighLabel<br>Label<br>Label<br>Label<br>Label<br>Label<br>Label<br>${
          constraintsToHtmlString(
            constraints
          )
        }"
      ),
      constraints
    )
    addLabel(0, 2, width = 2, height = 2)
    addLabel(2, 3, width = 3)

    return createTabPanel("Cells have different shapes", panel)
  }

  fun createCellAlignmentsPanel(): JPanel {
    val panel = createPanelLabels(HorizontalAlign.values().size, VerticalAlign.values().size) { grid, x, y ->
      Constraints(
        grid,
        x,
        y,
        horizontalAlign = HorizontalAlign.values()[x],
        verticalAlign = VerticalAlign.values()[y]
      )
    }
    val grid = (panel.layout as GridLayout).rootGrid
    grid.resizableColumns.addAll(0..HorizontalAlign.values().size)
    grid.resizableRows.addAll(0..VerticalAlign.values().size)
    return createTabPanel("Cells size is equal, component layouts have different alignments", panel)
  }

  fun createResizableCell11Panel(): JPanel {
    val panel = createPanelLabels(3, 4) { grid, x, y ->
      if (x == 1 && y == 1)
        Constraints(grid, x, y, horizontalAlign = HorizontalAlign.FILL, verticalAlign = VerticalAlign.FILL)
      else
        null
    }
    val grid = (panel.layout as GridLayout).rootGrid
    grid.resizableColumns.add(1)
    grid.resizableRows.add(1)
    return createTabPanel("One column and row are resizable", panel)
  }

  fun createSubGridPanel(): JPanel {
    val layoutManager = GridLayout()
    layoutManager.rootGrid.resizableColumns.add(1)
    layoutManager.rootGrid.resizableRows.add(1)
    val panel = JPanel(layoutManager)

    val subGrid = layoutManager.addLayoutSubGrid(
      Constraints(
        layoutManager.rootGrid, 1, 1,
        horizontalAlign = HorizontalAlign.FILL, verticalAlign = VerticalAlign.FILL
      )
    )
    subGrid.resizableColumns.add(1)
    subGrid.resizableRows.add(1)
    fillGridByLabels(panel, subGrid, 3, 3) { grid, x, y ->
      if (x == 1 && y == 1)
        Constraints(grid, x, y, horizontalAlign = HorizontalAlign.FILL, verticalAlign = VerticalAlign.FILL)
      else
        null
    }

    RowsGridBuilder(panel)
      .cell(label(0, 0))
      .cell(label(1, 0))
      .cell(label(2, 0))
      .row()
      .cell(label(0, 1))
      .skip()
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
    constraintFactory: (grid: Grid, x: Int, y: Int) -> Constraints?
  ): JPanel {
    val layoutManager = GridLayout()
    val result = JPanel(layoutManager)
    fillGridByLabels(result, layoutManager.rootGrid, width, height, constraintFactory)
    return result
  }

  fun fillGridByLabels(
    container: JComponent,
    grid: Grid,
    width: Int,
    height: Int,
    constraintFactory: (grid: Grid, x: Int, y: Int) -> Constraints?
  ) {
    for (x in 0 until width) {
      for (y in 0 until height) {
        val constraints =
          constraintFactory.invoke(grid, x, y) ?: Constraints(grid, x, y)

        container.addLabel(constraints, longLabel = x == y)
      }
    }
  }

  fun fillGridByCompoundLabels(
    container: JComponent,
    grid: Grid
  ) {
    fun addLabel(x: Int, y: Int, width: Int = 1, height: Int = 1) {
      container.addLabel(
        Constraints(
          grid, x, y, width = width, height = height,
          horizontalAlign = HorizontalAlign.FILL, verticalAlign = VerticalAlign.FILL
        )
      )
    }

    addLabel(0, 0, height = 2)
    addLabel(1, 0, width = 3)
    addLabel(4, 0, height = 3)
    addLabel(1, 1)
    val constraints = Constraints(
      grid, 2, 1, width = 2, height = 2,
      horizontalAlign = HorizontalAlign.FILL, verticalAlign = VerticalAlign.FILL
    )
    container.add(
      JLabel(
        "<html>HighLabel<br>Label<br>Label<br>Label<br>Label<br>Label<br>Label<br>${
          constraintsToHtmlString(
            constraints
          )
        }"
      ),
      constraints
    )
    addLabel(0, 2, width = 2, height = 2)
    addLabel(2, 3, width = 3)
  }

  fun label(constraints: Constraints, longLabel: Boolean = false): JLabel {
    val text = if (longLabel) "Very very very very very long label" else "Label"
    return JLabel("<html>$text<br>${constraintsToHtmlString(constraints)}")
  }

  fun constraintsToHtmlString(constraints: Constraints): String {
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

  fun label(x: Int, y: Int, longLabel: Boolean = false): JLabel {
    val text = if (longLabel) "Very very very very very long label" else "Label"
    return JLabel("$text [x = $x, y = $y]")
  }

  fun JComponent.addLabel(constraints: Constraints, longLabel: Boolean = false) {
    val label = label(constraints, longLabel)
    add(label, constraints)
  }
}

private fun gridToHtmlString(grid: Grid): String {
  val result = mutableListOf<String>()
  if (grid.resizableColumns.isNotEmpty()) {
    result.add("resizableColumns = ${grid.resizableColumns.joinToString()}")
  }
  if (grid.resizableRows.isNotEmpty()) {
    result.add("resizableRows = ${grid.resizableRows.joinToString()}")
  }
  if (grid.columnsGaps.isNotEmpty()) {
    result.add("<br>columnsGaps = ${grid.columnsGaps.joinToString()}")
  }
  if (grid.rowsGaps.isNotEmpty()) {
    result.add("<br>rowsGaps = ${grid.rowsGaps.joinToString()}")
  }
  return result.joinToString()
}

fun createTabPanel(title: String, content: JComponent): JPanel {
  val layoutManager = GridLayout()
  val rootGrid = layoutManager.rootGrid
  val result = JPanel(layoutManager)
  rootGrid.resizableColumns.add(0)
  rootGrid.resizableRows.add(1)
  val label = JLabel("<html>$title<br>${gridToHtmlString((content.layout as GridLayout).rootGrid)}")
  label.background = Color.LIGHT_GRAY
  label.isOpaque = true
  result.add(label, Constraints(rootGrid, 0, 0, width = 2, horizontalAlign = HorizontalAlign.FILL))
  result.add(
    content, Constraints(
    rootGrid, 0, 1, verticalAlign = VerticalAlign.FILL,
    horizontalAlign = HorizontalAlign.FILL
  )
  )

  val controlGrid = layoutManager.addLayoutSubGrid(
    Constraints(
      rootGrid,
      1,
      1,
      verticalAlign = VerticalAlign.FILL
    )
  )
  createControls(result, content, controlGrid)

  return result
}

private fun createControls(container: JComponent, content: JComponent, grid: Grid) {
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

  grid.resizableColumns.addAll(0..1)
  grid.resizableRows.add(0)
  container.add(
    JScrollPane(list), Constraints(
    grid, 0, 0, width = 2, horizontalAlign = HorizontalAlign.FILL,
    verticalAlign = VerticalAlign.FILL
  )
  )
  container.add(btnHide, Constraints(grid, 0, 1, horizontalAlign = HorizontalAlign.CENTER))
  container.add(btnShow, Constraints(grid, 1, 1, horizontalAlign = HorizontalAlign.CENTER))
  container.add(cbHighlight, Constraints(grid, 0, 2, width = 2))
}
