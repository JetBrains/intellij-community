// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.JLabel
import kotlin.reflect.KMutableProperty0

/**
 * Empty label parameter for [Panel.row] method in case label is omitted.
 */
val EMPTY_LABEL = String()

interface Panel : CellBase<Panel> {

  override fun visible(isVisible: Boolean): Panel

  override fun visibleIf(predicate: ComponentPredicate): Panel

  override fun enabled(isEnabled: Boolean): Panel

  override fun enabledIf(predicate: ComponentPredicate): Panel

  override fun horizontalAlign(horizontalAlign: HorizontalAlign): Panel

  override fun verticalAlign(verticalAlign: VerticalAlign): Panel

  override fun resizableColumn(): Panel

  override fun gap(rightGap: RightGap): Panel

  override fun customize(customGaps: Gaps): Panel

  /**
   * Adds standard left indent
   */
  fun indent(init: Panel.() -> Unit): RowsRange

  /**
   * Adds row with [RowLayout.LABEL_ALIGNED] layout and [label]. Use [EMPTY_LABEL] for empty label.
   * Do not use row(""), because it creates unnecessary label component in layout
   */
  fun row(@Nls label: String, init: Row.() -> Unit): Row

  /**
   * Adds row with [RowLayout.LABEL_ALIGNED] layout and [label]. If label is null then
   * [RowLayout.INDEPENDENT] layout is used
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
   * Adds horizontal line separator with optional [title]
   */
  fun separator(@NlsContexts.Separator title: String? = null, background: Color? = null): Row

  /**
   * Creates sub-panel that occupies the whole width and uses its own grid inside
   */
  fun panel(init: Panel.() -> Unit): Panel

  /**
   * @see [RowsRange]
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

  @Deprecated("Use overloaded group(...) instead")
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  fun group(@NlsContexts.BorderTitle title: String? = null,
            indent: Boolean = true,
            topGroupGap: Boolean? = null,
            bottomGroupGap: Boolean? = null,
            init: Panel.() -> Unit): Panel

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
   * and below (except the group in the parents last row) the group.
   * To change gaps around the group use [Row.topGap] and [Row.bottomGap] for the method result
   *
   * @param indent true if left indent is needed
   */
  fun collapsibleGroup(@NlsContexts.BorderTitle title: String,
                       indent: Boolean = true,
                       init: Panel.() -> Unit): CollapsibleRow

  @Deprecated("Use overloaded collapsibleGroup(...) instead")
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  fun collapsibleGroup(@NlsContexts.BorderTitle title: String,
                       indent: Boolean = true,
                       topGroupGap: Boolean? = null,
                       bottomGroupGap: Boolean? = null,
                       init: Panel.() -> Unit): CollapsiblePanel

  @Deprecated("Use buttonsGroup(...) instead")
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  fun buttonGroup(@NlsContexts.BorderTitle title: String? = null, indent: Boolean = title != null, init: Panel.() -> Unit)

  @Deprecated("Use buttonsGroup(...) instead")
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
  fun <T> buttonGroup(binding: PropertyBinding<T>, type: Class<T>, @NlsContexts.BorderTitle title: String? = null,
                      indent: Boolean = title != null, init: Panel.() -> Unit)

  /**
   * Unions [Row.radioButton] in one group. Must be also used for [Row.checkBox] if they are grouped with some title.
   * Note that [Panel.group] provides different gaps around the title

   * @param indent true if left indent is needed. By default, true if title exists and false otherwise
   */
  fun buttonsGroup(@NlsContexts.BorderTitle title: String? = null, indent: Boolean = title != null, init: Panel.() -> Unit): ButtonsGroup

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
   * Installs [propertyGraph] as validation requestor.
   * @see validationRequestor
   */
  fun propertyGraph(propertyGraph: PropertyGraph)

  /**
   * Registers custom validation requestor for all components.
   * @param validationRequestor gets callback (component validator) that should be subscribed on custom event.
   */
  fun validationRequestor(validationRequestor: (() -> Unit) -> Unit): Panel
}

@Deprecated("Use buttonsGroup(...) instead")
@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
inline fun <reified T : Any> Panel.buttonGroup(noinline getter: () -> T,
                                               noinline setter: (T) -> Unit,
                                               title: @NlsContexts.BorderTitle String? = null,
                                               indent: Boolean = title != null,
                                               crossinline init: Panel.() -> Unit) {
  buttonGroup(PropertyBinding(getter, setter), title, indent, init)
}

@Deprecated("Use buttonsGroup(...) instead")
@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
inline fun <reified T : Any> Panel.buttonGroup(prop: KMutableProperty0<T>, title: @NlsContexts.BorderTitle String? = null,
                                               indent: Boolean = title != null,
                                               crossinline init: Panel.() -> Unit) {
  buttonGroup(prop.toBinding(), title, indent, init)
}

@Deprecated("Use buttonsGroup(...) instead")
@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
inline fun <reified T : Any> Panel.buttonGroup(binding: PropertyBinding<T>, title: @NlsContexts.BorderTitle String? = null,
                                               indent: Boolean = title != null,
                                               crossinline init: Panel.() -> Unit) {
  buttonGroup(binding, T::class.java, title, indent) {
    init()
  }
}
