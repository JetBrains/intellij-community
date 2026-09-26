// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.ui.DialogPanel
import com.intellij.testFramework.TestApplicationManager
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.ui.dsl.gridLayout.Constraints
import com.intellij.ui.dsl.gridLayout.Grid
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import org.junit.Before
import org.junit.Test
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.text.JTextComponent
import kotlin.test.assertEquals

/**
 * Pins the grid `panel {}` builds: which cell each component goes in, how far it spans, how it is aligned and
 * what space is kept around it.
 *
 * These are `com.intellij.ui.dsl.builder.impl.PanelBuilder`'s decisions. Measuring the grid belongs to
 * [com.intellij.ui.dsl.gridLayout.GridLayout], which has tests of its own, and how wide a component wants to
 * be belongs to the component.
 *
 * Every form here uses [TestSpacing], whose gaps all differ from one another and none of which are the real
 * ones. A gap in an expectation therefore names the rule that produced it, and no expectation moves with the
 * look and feel. Visual paddings are left out for the same reason: the component decides them, not this code.
 *
 * A line is one cell: `[x,y]` in the grid it belongs to, then the span, alignment and gaps that differ from
 * the default, then either a component or a grid of its own, whose cells follow indented. A line starting with
 * `*` is what a grid itself was given: its resizable rows and columns, and the space above and below each row
 * as `top/bottom`.
 */
class PanelGridStructureTest {

  @Before
  fun before() {
    TestApplicationManager.getInstance()
  }

  @Test
  fun rowWithLabel() = assertGrid(
    """
    * resizableColumns=[1] rowsGaps=0/0, 0/0, 0/8
    [0,0] gaps=7/0/7/1 JLabel "Name:"
    [1,0] FILL grid
      * resizableColumns=[0] resizableRows=[0]
      [0,0] gaps=7/0/7/0 JBTextField
    [0,1] gaps=7/0/7/1 JLabel "Longer label:"
    [1,1] FILL grid
      * resizableColumns=[0] resizableRows=[0]
      [0,0] gaps=7/0/7/0 JBTextField
    """
  ) {
    row("Name:") { textField() }
    row("Longer label:") { textField() }
  }

  @Test
  fun rowWithSeveralCells() = assertGrid(
    """
    * resizableColumns=[1] rowsGaps=0/0, 0/0, 0/8
    [0,0] gaps=7/0/7/1 JLabel "One:"
    [1,0] FILL grid
      * resizableColumns=[0] resizableRows=[0]
      [0,0] gaps=7/0/7/0 JBTextField
    [0,1] gaps=7/0/7/1 JLabel "Three:"
    [1,1] FILL grid
      * resizableColumns=[2] resizableRows=[0]
      [0,0] gaps=7/0/7/2 JBTextField
      [1,0] gaps=7/0/7/2 JLabel "of"
      [2,0] gaps=7/0/7/0 JBTextField
    """
  ) {
    row("One:") { textField() }
    row("Three:") {
      textField()
      label("of")
      textField()
    }
  }

  @Test
  fun labelBeforeCheckBoxTakesAWiderGap() = assertGrid(
    """
    * resizableColumns=[1] rowsGaps=0/0, 0/0, 0/8
    [0,0] gaps=7/0/7/2 JLabel "Mode:"
    [1,0] FILL grid
      * resizableColumns=[0] resizableRows=[0]
      [0,0] gaps=7/0/7/0 JBCheckBox "Brave"
    [0,1] gaps=7/0/7/1 JLabel "Text:"
    [1,1] FILL grid
      * resizableColumns=[0] resizableRows=[0]
      [0,0] gaps=7/0/7/0 JBTextField
    """
  ) {
    row("Mode:") { checkBox("Brave") }
    row("Text:") { textField() }
  }

  @Test
  fun independentRow() = assertGrid(
    """
    * resizableColumns=[1] rowsGaps=0/0, 0/0
    [0,0] gaps=7/0/7/1 JLabel "Aligned:"
    [1,0] FILL grid
      * resizableColumns=[0] resizableRows=[0]
      [0,0] gaps=7/0/7/0 JBTextField
    [0,1] w2 FILL gaps=0/0/0/0 grid
      * resizableColumns=[2] resizableRows=[0] rowsGaps=0/0, 0/8
      [0,0] gaps=7/0/7/1 JLabel "Independent:"
      [1,0] gaps=7/0/7/2 JBTextField
      [2,0] gaps=7/0/7/0 JBTextField
    """
  ) {
    row("Aligned:") { textField() }
    row("Independent:") {
      textField()
      textField()
    }.layout(RowLayout.INDEPENDENT)
  }

  @Test
  fun parentGridRow() = assertGrid(
    """
    * resizableColumns=[2] rowsGaps=0/0, 0/0, 0/8
    [0,0] gaps=7/0/7/1 JLabel "A:"
    [1,0] gaps=7/0/7/2 JBTextField
    [2,0] gaps=7/0/7/0 JBTextField
    [0,1] gaps=7/0/7/1 JLabel "B:"
    [1,1] gaps=7/0/7/2 JBTextField
    [2,1] gaps=7/0/7/0 JBTextField
    """
  ) {
    row("A:") {
      textField()
      textField()
    }.layout(RowLayout.PARENT_GRID)
    row("B:") {
      textField()
      textField()
    }.layout(RowLayout.PARENT_GRID)
  }

  @Test
  fun cellComment() = assertGrid(
    """
    * resizableColumns=[1] rowsGaps=0/0, 0/0, 0/0, 0/8
    [0,0] gaps=7/0/7/1 JLabel "Port:"
    [1,0] FILL grid
      * resizableColumns=[0] resizableRows=[0]
      [0,0] gaps=7/0/7/0 JBTextField
    [1,1] TOP noBaseline gaps=0/0/7/0 DslLabel "0 picks a free port"
    [0,2] gaps=7/0/7/1 JLabel "Host:"
    [1,2] FILL grid
      * resizableColumns=[0] resizableRows=[0]
      [0,0] gaps=7/0/7/0 JBTextField
    """
  ) {
    row("Port:") { textField().comment("0 picks a free port") }
    row("Host:") { textField() }
  }

  @Test
  fun rowComment() = assertGrid(
    """
    * resizableColumns=[1] rowsGaps=0/0, 0/0
    [0,0] gaps=7/0/7/1 JLabel "Port:"
    [1,0] FILL grid
      * resizableColumns=[0] resizableRows=[0]
      [0,0] gaps=7/0/7/0 JBTextField
    [0,1] w2 gaps=0/0/7/0 DslLabel "About the whole row"
    """
  ) {
    row("Port:") { textField() }.rowComment("About the whole row")
  }

  @Test
  fun commentUnderAToggleButtonStepsInPastItsBox() = assertGrid(
    """
    * resizableColumns=[0] rowsGaps=0/0
    [0,0] FILL gaps=0/0/0/0 grid
      * resizableColumns=[0] resizableRows=[0] rowsGaps=0/0, 0/8
      [0,0] gaps=7/0/7/0 JBCheckBox "Use a proxy"
      [0,1] TOP noBaseline gaps=0/6/7/0 DslLabel "Only when the host is set"
    """
  ) {
    row { checkBox("Use a proxy").comment("Only when the host is set") }
  }

  @Test
  fun commentRightAndContextHelpShareASubGrid() = assertGrid(
    """
    * resizableColumns=[1] rowsGaps=0/0, 0/8
    [0,0] gaps=7/0/7/1 JLabel "Port:"
    [1,0] FILL grid
      * resizableColumns=[0] resizableRows=[0]
      [0,0] gaps=7/0/7/0 grid
        [0,0] JBTextField
        [1,0] gaps=0/1/0/0 ContextHelpLabel
        [2,0] gaps=0/4/0/0 DslLabel "beside"
    """
  ) {
    row("Port:") { textField().commentRight("beside").contextHelp("help") }
  }

  @Test
  fun labelAboveItsCell() = assertGrid(
    """
    * resizableColumns=[0] rowsGaps=0/0
    [0,0] FILL gaps=0/0/0/0 grid
      * resizableColumns=[0] resizableRows=[1] rowsGaps=0/0, 0/0, 0/8
      [0,0] BOTTOM noBaseline gaps=7/0/0/0 JLabel "Above:"
      [0,1] gaps=7/0/7/0 JBTextField
    """
  ) {
    row { textField().label("Above:", LabelPosition.TOP) }
  }

  @Test
  fun resizableRowAndFilledCell() = assertGrid(
    """
    * resizableColumns=[1] resizableRows=[0] rowsGaps=0/0, 0/0, 0/8
    [0,0] gaps=7/0/7/1 JLabel "Log:"
    [1,0] FILL FILL grid
      * resizableColumns=[0] resizableRows=[0]
      [0,0] FILL gaps=7/0/7/0 JBTextField
    [0,1] gaps=7/0/7/1 JLabel "After:"
    [1,1] FILL grid
      * resizableColumns=[0] resizableRows=[0]
      [0,0] gaps=7/0/7/0 JBTextField
    """
  ) {
    row("Log:") { textField().align(AlignX.FILL) }.resizableRow()
    row("After:") { textField() }
  }

  @Test
  fun explicitRightGaps() = assertGrid(
    """
    * resizableColumns=[0] rowsGaps=0/0
    [0,0] FILL gaps=0/0/0/0 grid
      * resizableColumns=[2] resizableRows=[0] rowsGaps=0/0, 0/8
      [0,0] gaps=7/0/7/1 JBTextField
      [1,0] gaps=7/0/7/3 JBTextField
      [2,0] gaps=7/0/7/0 JBTextField
    """
  ) {
    row {
      textField().gap(RightGap.SMALL)
      textField().gap(RightGap.COLUMNS)
      textField()
    }
  }

  @Test
  fun neighbouringRowGapsCollapseToTheLarger() = assertGrid(
    """
    * resizableColumns=[0] rowsGaps=0/0, 10/0, 9/0
    [0,0] FILL gaps=0/0/0/0 grid
      * resizableColumns=[0] resizableRows=[0] rowsGaps=0/0, 0/8
      [0,0] gaps=7/0/7/0 JBTextField
    [0,1] FILL gaps=0/0/0/0 grid
      * resizableColumns=[0] resizableRows=[0] rowsGaps=0/0, 0/8
      [0,0] gaps=7/0/7/0 JBTextField
    [0,2] FILL gaps=0/0/0/0 grid
      * resizableColumns=[0] resizableRows=[0] rowsGaps=0/0, 0/8
      [0,0] gaps=7/0/7/0 JBTextField
    """
  ) {
    // The first row asks for 9 below it and the second for 10 above it: the larger is kept and the other
    // dropped, so the two are separated once rather than twice.
    row { textField() }.bottomGap(BottomGap.SMALL)
    row { textField() }.topGap(TopGap.MEDIUM)
    row { textField() }.topGap(TopGap.SMALL)
  }

  @Test
  fun groupIndentAndSeparator() = assertGrid(
    """
    * resizableColumns=[0] rowsGaps=0/0, 10/10, 0/0, 0/0
    [0,0] FILL gaps=0/0/0/0 grid
      * resizableColumns=[0] resizableRows=[0] rowsGaps=0/0, 0/8
      [0,0] gaps=7/0/7/0 JBCheckBox "Before"
    [0,1] FILL gaps=0/0/0/0 grid
      * resizableColumns=[0] resizableRows=[0] rowsGaps=0/0, 0/8
      [0,0] FILL gaps=0/0/0/0 grid
        * resizableColumns=[1] rowsGaps=0/0, 0/0, 0/8
        [0,0] w2 FILL gaps=0/0/0/0 grid
          * resizableColumns=[0] resizableRows=[0] rowsGaps=0/0, 0/8
          [0,0] FILL gaps=7/0/7/0 TitledSeparator
        [0,1] gaps=7/5/7/1 JLabel "Host:"
        [1,1] FILL grid
          * resizableColumns=[0] resizableRows=[0]
          [0,0] gaps=7/0/7/0 JBTextField
    [0,2] FILL gaps=0/0/0/0 grid
      * resizableColumns=[0] resizableRows=[0] rowsGaps=0/0, 0/8
      [0,0] FILL gaps=7/0/7/0 SeparatorComponent
    [0,3] FILL gaps=0/5/0/0 grid
      * resizableColumns=[0] resizableRows=[0] rowsGaps=0/0, 0/8
      [0,0] gaps=7/0/7/0 JBCheckBox "Indented"
    """
  ) {
    row { checkBox("Before") }
    group("Proxy") {
      row("Host:") { textField() }
    }
    separator()
    indent {
      row { checkBox("Indented") }
    }
  }

  @Test
  fun nestedPanelIsASubGrid() = assertGrid(
    """
    * resizableColumns=[1] rowsGaps=0/0, 0/0
    [0,0] gaps=7/0/7/1 JLabel "Outer:"
    [1,0] FILL grid
      * resizableColumns=[0] resizableRows=[0]
      [0,0] gaps=7/0/7/0 JBTextField
    [0,1] w2 FILL gaps=0/0/0/0 grid
      * resizableColumns=[0] resizableRows=[0] rowsGaps=0/0, 0/8
      [0,0] gaps=0/0/0/0 grid
        * resizableColumns=[1] rowsGaps=0/0, 0/8
        [0,0] gaps=7/0/7/1 JLabel "Inner:"
        [1,0] FILL grid
          * resizableColumns=[0] resizableRows=[0]
          [0,0] gaps=7/0/7/0 JBTextField
    """
  ) {
    // A panel in a cell keeps its components in the same container: it becomes a grid of the form's grid,
    // not a panel of its own, which is why nothing inside it lines up with anything outside it.
    row("Outer:") { textField() }
    row {
      panel {
        row("Inner:") { textField() }
      }
    }
  }

  @Test
  fun placeholderHoldsItsCellEmpty() = assertGrid(
    """
    * resizableColumns=[1] rowsGaps=0/0, 0/0, 0/8
    [0,0] gaps=7/0/7/1 JLabel "Slot:"
    [0,1] gaps=7/0/7/1 JLabel "After:"
    [1,1] FILL grid
      * resizableColumns=[0] resizableRows=[0]
      [0,0] gaps=7/0/7/0 JBTextField
    """
  ) {
    // The cell at [1,0] is kept for whatever the placeholder is later given, and holds nothing until then.
    row("Slot:") { placeholder() }
    row("After:") { textField() }
  }

  @Test
  fun widthGroupTravelsWithTheCell() = assertGrid(
    """
    * resizableColumns=[1] rowsGaps=0/0, 0/0, 0/8
    [0,0] gaps=7/0/7/1 JLabel "A:"
    [1,0] FILL grid
      * resizableColumns=[0] resizableRows=[0]
      [0,0] gaps=7/0/7/0 widthGroup=fields JBTextField
    [0,1] gaps=7/0/7/1 JLabel "Longer:"
    [1,1] FILL grid
      * resizableColumns=[0] resizableRows=[0]
      [0,0] gaps=7/0/7/0 widthGroup=fields JBTextField
    """
  ) {
    row("A:") { textField().widthGroup("fields") }
    row("Longer:") { textField().widthGroup("fields") }
  }

  @Test
  fun customGapsReplaceTheOnesTheRuleWouldGive() = assertGrid(
    """
    * resizableColumns=[1] rowsGaps=0/0, 0/8
    [0,0] gaps=7/0/7/1 JLabel "Name:"
    [1,0] FILL grid
      * resizableColumns=[0] resizableRows=[0]
      [0,0] gaps=11/12/13/14 JBTextField
    """
  ) {
    row("Name:") { textField().customize(UnscaledGaps(11, 12, 13, 14)) }
  }

  // --- The comparison ---------------------------------------------------------------------------

  private fun assertGrid(expected: String, init: Panel.() -> Unit) {
    val panel = panel { customizeSpacingConfiguration(TestSpacing(), init) }
    assertEquals(expected.trimIndent().trim(), dumpGrid(panel).trim())
  }
}

/**
 * All different from one another, and none of them a real gap, so a number in an expectation names the rule
 * that put it there.
 */
private class TestSpacing : SpacingConfiguration {
  override val horizontalSmallGap: Int = 1
  override val horizontalDefaultGap: Int = 2
  override val horizontalColumnsGap: Int = 3
  override val horizontalCommentGap: Int = 4
  override val horizontalIndent: Int = 5
  override val horizontalToggleButtonIndent: Int = 6
  override val verticalComponentGap: Int = 7
  override val verticalCommentBottomGap: Int = 8
  override val verticalSmallGap: Int = 9
  override val verticalMediumGap: Int = 10
  override val buttonGroupHeaderBottomGap: Int = 11
  override val segmentedButtonVerticalGap: Int = 12
  override val segmentedButtonHorizontalGap: Int = 13
}

/** One thing placed in a grid: a component, or a grid of its own. */
private sealed class Placed(val constraints: Constraints) {
  class Component(constraints: Constraints, val component: JComponent) : Placed(constraints)
  class SubGrid(constraints: Constraints, val grid: Grid) : Placed(constraints)
}

/** The grid of [panel] as text, a line per cell, the cells of a sub-grid indented under the cell it occupies. */
private fun dumpGrid(panel: DialogPanel): String {
  val layout = panel.layout as GridLayout
  val contents = mutableMapOf<Grid, MutableList<Placed>>()

  for (component in panel.components) {
    val child = component as JComponent
    val constraints = layout.getConstraints(child) ?: continue
    contents.getOrPut(constraints.grid) { mutableListOf() } += Placed.Component(constraints, child)

    // Every grid between this one and the root holds the one below it, so walking up from the component is
    // what finds the sub-grids: a Grid does not say what it holds.
    var grid = constraints.grid
    while (grid !== layout.rootGrid) {
      val gridConstraints = layout.getConstraints(grid) ?: break
      val siblings = contents.getOrPut(gridConstraints.grid) { mutableListOf() }
      if (siblings.none { it is Placed.SubGrid && it.grid === grid }) {
        siblings += Placed.SubGrid(gridConstraints, grid)
      }
      grid = gridConstraints.grid
    }
  }

  return buildString { appendGrid(layout.rootGrid, "", contents) }
}

private fun StringBuilder.appendGrid(grid: Grid, indent: String, contents: Map<Grid, List<Placed>>) {
  describeGrid(grid)?.let { append(indent).append(it).append('\n') }

  val placed = contents[grid].orEmpty().sortedWith(compareBy({ it.constraints.y }, { it.constraints.x }))
  for (item in placed) {
    append(indent).append(describe(item.constraints))
    when (item) {
      is Placed.Component -> append(' ').append(describe(item.component)).append('\n')
      is Placed.SubGrid -> {
        append(" grid\n")
        appendGrid(item.grid, "$indent  ", contents)
      }
    }
  }
}

private fun describeGrid(grid: Grid): String? {
  val parts = buildList {
    if (grid.resizableColumns.isNotEmpty()) add("resizableColumns=${grid.resizableColumns.sorted()}")
    if (grid.resizableRows.isNotEmpty()) add("resizableRows=${grid.resizableRows.sorted()}")
    val rowsGaps = grid.rowsGaps.dropLastWhile { it == UnscaledGapsY.EMPTY }
    if (rowsGaps.isNotEmpty()) add("rowsGaps=${rowsGaps.joinToString { "${it.top}/${it.bottom}" }}")
  }
  return if (parts.isEmpty()) null else parts.joinToString(" ", prefix = "* ")
}

private fun describe(constraints: Constraints): String = buildString {
  append("[${constraints.x},${constraints.y}]")
  if (constraints.width != 1) append(" w${constraints.width}")
  if (constraints.horizontalAlign != HorizontalAlign.LEFT) append(" ${constraints.horizontalAlign}")
  if (constraints.verticalAlign != VerticalAlign.CENTER) append(" ${constraints.verticalAlign}")
  if (!constraints.baselineAlign) append(" noBaseline")
  if (constraints.gaps != UnscaledGaps.EMPTY) {
    with(constraints.gaps) { append(" gaps=$top/$left/$bottom/$right") }
  }
  constraints.widthGroup?.let { append(" widthGroup=$it") }
}

private fun describe(component: JComponent): String {
  val kind = generateSequence<Class<*>>(component.javaClass) { it.superclass }.first { !it.isAnonymousClass }.simpleName
  val text = when (component) {
    // A comment holds the html it generated, colours and all, so what it was given is what identifies it.
    is DslLabel -> component.userText
    is AbstractButton -> component.text
    is JLabel -> component.text
    is JTextComponent -> component.text
    else -> null
  }
  return if (text.isNullOrEmpty()) kind else "$kind \"$text\""
}
