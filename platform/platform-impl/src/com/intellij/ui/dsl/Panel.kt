// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JLabel
import kotlin.reflect.KMutableProperty0

@ApiStatus.Experimental
interface Panel : CellBase<Panel> {

  override fun visible(isVisible: Boolean): Panel

  override fun enabled(isEnabled: Boolean): Panel

  fun enabledIf(predicate: ComponentPredicate): Panel

  override fun horizontalAlign(horizontalAlign: HorizontalAlign): Panel

  override fun verticalAlign(verticalAlign: VerticalAlign): Panel

  override fun resizableColumn(): Panel

  override fun gap(rightGap: RightGap): Panel

  /**
   * Adds standard left indent
   */
  fun indent(init: Panel.() -> Unit)

  fun row(@Nls label: String, init: Row.() -> Unit): Row

  fun row(label: JLabel? = null, init: Row.() -> Unit): Row

  /**
   * Adds specified columns in a row
   */
  fun twoColumnRow(column1: (Row.() -> Unit)?, column2: (Row.() -> Unit)? = null): Row

  /**
   * Creates sub-panel that occupies whole width and uses own grid inside
   */
  fun panel(init: Panel.() -> Unit): Panel

  /**
   * See [RowsRange]
   */
  fun rowsRange(init: Panel.() -> Unit): RowsRange

  /**
   * Adds panel with a title and some vertical space before the group. Grouped radio buttons and checkboxes should use [Panel.buttonGroup]
   * method, which uses different title gaps
   *
   * @param indent true left indent is needed
   */
  fun group(@NlsContexts.BorderTitle title: String? = null, indent: Boolean = true, init: Panel.() -> Unit): Panel

  /**
   * See [RowsRange]
   */
  fun groupRowsRange(@NlsContexts.BorderTitle title: String? = null, init: Panel.() -> Unit): RowsRange

  /**
   * Unions [Row.radioButton] in one group. Must be also used for [Row.checkBox] if they are grouped with some title.
   * Note that [Panel.group] provides different gaps around the title
   */
  fun <T> buttonGroup(binding: PropertyBinding<T>, type: Class<T>, @NlsContexts.BorderTitle title: String? = null, init: Panel.() -> Unit)

}

inline fun <reified T : Any> Panel.buttonGroup(noinline getter: () -> T,
                                               noinline setter: (T) -> Unit,
                                               @NlsContexts.BorderTitle title: String? = null,
                                               crossinline init: Panel.() -> Unit) {
  buttonGroup(PropertyBinding(getter, setter), title, init)
}

inline fun <reified T : Any> Panel.buttonGroup(prop: KMutableProperty0<T>, @NlsContexts.BorderTitle title: String? = null,
                                               crossinline init: Panel.() -> Unit) {
  buttonGroup(prop.toBinding(), title, init)
}

inline fun <reified T : Any> Panel.buttonGroup(binding: PropertyBinding<T>, @NlsContexts.BorderTitle title: String? = null,
                                               crossinline init: Panel.() -> Unit) {
  buttonGroup(binding, T::class.javaPrimitiveType ?: T::class.java, title) {
    init()
  }
}
