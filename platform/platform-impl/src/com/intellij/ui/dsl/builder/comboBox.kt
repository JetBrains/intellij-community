// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.impl.CellImpl.Companion.installValidationRequestor
import com.intellij.ui.dsl.builder.impl.toBindingInternal
import com.intellij.ui.layout.*
import kotlin.reflect.KMutableProperty0

fun <T, C : ComboBox<T>> Cell<C>.bindItem(binding: PropertyBinding<T?>): Cell<C> {
  return bind({ component -> component.selectedItem as T? },
    { component, value -> component.setSelectedItem(value) },
    binding)
}

@Deprecated("Please, recompile code", level = DeprecationLevel.HIDDEN)
fun <T, C : ComboBox<T>> Cell<C>.bindItem(property: GraphProperty<T>) = bindItem(property)

fun <T, C : ComboBox<T>> Cell<C>.bindItem(property: ObservableMutableProperty<T>): Cell<C> {
  installValidationRequestor(property)
  return applyToComponent { bind(property) }
}

inline fun <reified T : Any, C : ComboBox<T>> Cell<C>.bindItem(prop: KMutableProperty0<T>): Cell<C> {
  return bindItem(prop.toBindingInternal().toNullable())
}

fun <T, C : ComboBox<T>> Cell<C>.bindItem(getter: () -> T?, setter: (T?) -> Unit): Cell<C> {
  return bindItem(PropertyBinding(getter, setter))
}

/**
 * Minimal width of combobox in chars
 *
 * @see COLUMNS_TINY
 * @see COLUMNS_SHORT
 * @see COLUMNS_MEDIUM
 * @see COLUMNS_LARGE
 */
fun <T, C : ComboBox<T>> Cell<C>.columns(columns: Int) = apply {
  component.columns(columns)
}

fun <T, C : ComboBox<T>> C.columns(columns: Int) = apply {
  // See JTextField.getColumnWidth implementation
  val columnWidth = getFontMetrics(font).charWidth('m')
  setMinimumAndPreferredWidth(columns * columnWidth + insets.left + insets.right)
}
