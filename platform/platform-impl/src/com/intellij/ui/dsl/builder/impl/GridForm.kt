// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.SpacingConfiguration
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.ui.dsl.gridLayout.Constraints
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * Lays [rows] out as a grid, through [builder].
 *
 * [builder] decides how the components get into the container: one made over a panel adds each component as
 * it places it, one made over a container whose components are in it already registers them where they are.
 * [spacing] is the form's own; a nested form may use another.
 */
@ApiStatus.Internal
fun buildGridForm(rows: List<GridFormRow>, builder: RowsGridBuilder, spacing: SpacingConfiguration) {
  PanelBuilder(rows, spacing, builder).build()
}

/**
 * A row of a form, and what it asked for. Nothing here is a decision: a row says it wants a medium gap above
 * it, not that it starts 20 pixels down, and which column a cell lands in is [buildGridForm]'s answer.
 *
 * This is what a front-end describes its form in, so bindings, validation and recomposition never reach the
 * layout.
 */
@ApiStatus.Internal
class GridFormRow(
  val rowLayout: RowLayout,
  val cells: List<GridFormCell?>,

  /** How far the row steps in from the left edge of its form, in unscaled pixels. */
  val indent: Int = 0,

  /** Whether the row takes the vertical space the form has left over. */
  val resizableRow: Boolean = false,

  /** A comment under the whole row, reaching across all of its columns. */
  val rowComment: DslLabel? = null,

  /** Space above and below the row, in place of everything else that would decide it. */
  val customGaps: UnscaledGapsY? = null,
  val topGap: TopGap? = null,
  val bottomGap: BottomGap? = null,

  /** The space the row keeps from its neighbors when it asks for no [topGap] or [bottomGap] of its own. */
  val internalGaps: UnscaledGapsY = UnscaledGapsY.EMPTY,

  /** Where the row was declared, to point at when something is wrong with it. */
  val creationStackTrace: Throwable? = null,
)

/** One cell of a [GridFormRow], and what it asked for. */
@ApiStatus.Internal
sealed class GridFormCell(
  val horizontalAlign: HorizontalAlign,
  val verticalAlign: VerticalAlign,
  val resizableColumn: Boolean,
  val rightGap: RightGap?,
  val customGaps: UnscaledGaps?,
)

/**
 * A cell holding a component.
 *
 * [component] answers for the cell: a label may be assigned to it, and the properties of
 * [com.intellij.ui.dsl.builder.DslComponentProperty] are read from it. [viewComponent] is the one that goes
 * into the grid. The two differ when a component is shown wrapped in another, a text field with a browse
 * button, say.
 */
@ApiStatus.Internal
class GridFormComponentCell(
  val component: JComponent,
  val viewComponent: JComponent = component,
  val label: JLabel? = null,
  val labelPosition: LabelPosition = LabelPosition.LEFT,
  val comment: DslLabel? = null,
  val commentRight: DslLabel? = null,
  val contextHelpLabel: JComponent? = null,
  /** What the context help says, for the message when a cell asks for something it cannot have. */
  val contextHelpDescription: String? = null,
  val widthGroup: String? = null,
  horizontalAlign: HorizontalAlign = HorizontalAlign.LEFT,
  verticalAlign: VerticalAlign = VerticalAlign.CENTER,
  resizableColumn: Boolean = false,
  rightGap: RightGap? = null,
  customGaps: UnscaledGaps? = null,
) : GridFormCell(horizontalAlign, verticalAlign, resizableColumn, rightGap, customGaps)

/**
 * A cell holding a form of its own. It becomes a grid inside the cell rather than a panel: the components stay
 * in the same container, and the columns of the inner form settle inside the cell without moving anything
 * outside it.
 */
@ApiStatus.Internal
class GridFormPanelCell(
  val rows: List<GridFormRow>,
  val spacingConfiguration: SpacingConfiguration,
  horizontalAlign: HorizontalAlign = HorizontalAlign.LEFT,
  verticalAlign: VerticalAlign = VerticalAlign.CENTER,
  resizableColumn: Boolean = false,
  rightGap: RightGap? = null,
  customGaps: UnscaledGaps? = null,
) : GridFormCell(horizontalAlign, verticalAlign, resizableColumn, rightGap, customGaps)

/**
 * A cell kept for a component that is not there yet. [place] is handed the cell, to put something in whenever
 * there is something to put.
 */
@ApiStatus.Internal
class GridFormDeferredCell(
  val place: (Constraints) -> Unit,
  horizontalAlign: HorizontalAlign = HorizontalAlign.LEFT,
  verticalAlign: VerticalAlign = VerticalAlign.CENTER,
  resizableColumn: Boolean = false,
  rightGap: RightGap? = null,
  customGaps: UnscaledGaps? = null,
) : GridFormCell(horizontalAlign, verticalAlign, resizableColumn, rightGap, customGaps)
