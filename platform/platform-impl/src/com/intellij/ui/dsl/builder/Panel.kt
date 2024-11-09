// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.ComponentPredicate
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.JLabel

@ApiStatus.NonExtendable
@JvmDefaultWithCompatibility
interface Panel : CellBase<Panel> {

  override fun visible(isVisible: Boolean): Panel

  override fun visibleIf(predicate: ComponentPredicate): Panel

  override fun enabled(isEnabled: Boolean): Panel

  override fun enabledIf(predicate: ComponentPredicate): Panel

  @Deprecated("Use align(AlignX.LEFT/CENTER/RIGHT/FILL) method instead", level = DeprecationLevel.HIDDEN)
  @ApiStatus.ScheduledForRemoval
  override fun horizontalAlign(horizontalAlign: HorizontalAlign): Panel

  @Deprecated("Use align(AlignY.TOP/CENTER/BOTTOM/FILL) method instead", level = DeprecationLevel.HIDDEN)
  @ApiStatus.ScheduledForRemoval
  override fun verticalAlign(verticalAlign: VerticalAlign): Panel

  override fun align(align: Align): Panel

  override fun resizableColumn(): Panel

  override fun gap(rightGap: RightGap): Panel

  @Deprecated("Use customize(UnscaledGaps) instead", level = DeprecationLevel.HIDDEN)
  @ApiStatus.ScheduledForRemoval
  override fun customize(customGaps: Gaps): Panel

  override fun customize(customGaps: UnscaledGaps): Panel

  /**
   * Adds standard left indent and groups rows into [RowsRange] that allows to use some groups operations on the rows
   *
   * @see [rowsRange]
   */
  fun indent(init: Panel.() -> Unit): RowsRange

  /**
   * Adds row with [RowLayout.LABEL_ALIGNED] layout and [label]. The label can contain mnemonic and is assigned
   * to the first component in the row via [JLabel.labelFor] property.
   * Use row("") if label is empty
   */
  fun row(@Nls label: String, init: Row.() -> Unit): Row

  /**
   * Adds row with [RowLayout.LABEL_ALIGNED] layout and [label]. The label is assigned
   * to the first component in the row via [JLabel.labelFor] property.
   * If label is null then [RowLayout.INDEPENDENT] layout is used
   */
  fun row(label: JLabel? = null, init: Row.() -> Unit): Row

  /**
   * Adds specified columns in a row
   */
  fun twoColumnsRow(column1: (Row.() -> Unit)?, column2: (Row.() -> Unit)? = null): Row

  /**
   * Adds specified columns in a row
   */
  fun threeColumnsRow(column1: (Row.() -> Unit)?, column2: (Row.() -> Unit)? = null, column3: (Row.() -> Unit)? = null): Row

  /**
   * Adds horizontal line separator. Use [group] or [groupRowsRange] if you need a separator with title
   */
  fun separator(background: Color? = null): Row

  /**
   * Creates sub-panel that occupies the whole width and uses its own grid inside
   */
  fun panel(init: Panel.() -> Unit): Panel

  /**
   * Groups rows into [RowsRange] that allows to use some groups operations on the rows
   *
   * @see [indent]
   */
  fun rowsRange(init: Panel.() -> Unit): RowsRange

  /**
   * Adds panel with independent grid, title and some vertical space above (except the group in the parents first row)
   * and below (except the group in the parents last row) the group.
   * Grouped radio buttons and checkboxes should use [Panel.buttonsGroup] method, which uses different title gaps.
   * To change gaps around the group use [Row.topGap] and [Row.bottomGap] for the method result
   *
   * @param indent true if left indent is needed
   */
  fun group(@NlsContexts.BorderTitle title: String? = null,
            indent: Boolean = true,
            init: Panel.() -> Unit): Row

  /**
   * See overloaded method
   */
  fun group(title: JBLabel, indent: Boolean = true, init: Panel.() -> Unit): Row

  /**
   * Similar to [Panel.group] but uses the same grid as the parent.
   *
   * @see [RowsRange]
   */
  fun groupRowsRange(@NlsContexts.BorderTitle title: String? = null,
                     indent: Boolean = true,
                     topGroupGap: Boolean? = null,
                     bottomGroupGap: Boolean? = null,
                     init: Panel.() -> Unit): RowsRange

  /**
   * Adds collapsible panel with independent grid, title and some vertical space above (except the group in the parents first row)
   * and below (except the group in the parents last row) the group. The group title is focusable via the Tab key and supports mnemonics.
   * To change gaps around the group use [Row.topGap] and [Row.bottomGap] for the method result
   *
   * @param indent true if left indent is needed
   */
  fun collapsibleGroup(@NlsContexts.BorderTitle title: String,
                       indent: Boolean = true,
                       init: Panel.() -> Unit): CollapsibleRow

  /**
   * Unions [Row.radioButton] in one group. Must be also used for [Row.checkBox] if they are grouped with some title.
   * Note that [Panel.group] provides different gaps around the title

   * @param indent true if left indent is needed. By default, true if title exists and false otherwise
   */
  fun buttonsGroup(@NlsContexts.Label title: String? = null, indent: Boolean = title != null, init: Panel.() -> Unit): ButtonsGroup

  /**
   * Registers [callback] that will be called from [DialogPanel.apply] method
   */
  fun onApply(callback: () -> Unit): Panel

  /**
   * Registers [callback] that will be called from [DialogPanel.reset] method
   */
  fun onReset(callback: () -> Unit): Panel

  /**
   * Registers [callback] that will be called from [DialogPanel.isModified] method
   */
  fun onIsModified(callback: () -> Boolean): Panel

  /**
   * Overrides default spacing configuration. Should be used for very specific cases
   */
  fun customizeSpacingConfiguration(spacingConfiguration: SpacingConfiguration, init: Panel.() -> Unit)

  /**
   * Forces to use new [com.intellij.ui.dsl.listCellRenderer.textListCellRenderer] as the default renderer in combo boxes inside panel.
   * In the near release [textListCellRenderer] will be used by default in Kotlin UI DSL and this method will be removed
   */
  @ApiStatus.Internal
  @ApiStatus.Experimental
  fun useNewComboBoxRenderer()
}
