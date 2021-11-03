// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.layout.*
import kotlin.reflect.KMutableProperty0

fun <T, C : ComboBox<T>> Cell<C>.bindItem(binding: PropertyBinding<T?>): Cell<C> {
  return bind({ component -> component.selectedItem as T? },
    { component, value -> component.setSelectedItem(value) },
    binding)
}

fun <T, C : ComboBox<T>> Cell<C>.bindItem(property: GraphProperty<T>): Cell<C> {
  component.selectedItem = property.get()
  return graphProperty(property)
    .applyToComponent { bind(property) }
}

inline fun <reified T : Any, C : ComboBox<T>> Cell<C>.bindItem(prop: KMutableProperty0<T>): Cell<C> {
  return bindItem(prop.toBinding().toNullable())
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
