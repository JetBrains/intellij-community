// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.impl.CellImpl.Companion.installValidationRequestor
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KMutableProperty0

@Deprecated("Use overloaded method")
fun <T, C : ComboBox<T>> Cell<C>.bindItem(binding: PropertyBinding<T?>): Cell<C> {
  return bindItem(MutableProperty(binding.get, binding.set))
}

fun <T, C : ComboBox<T>> Cell<C>.bindItem(prop: MutableProperty<T?>): Cell<C> {
  return bind({ component -> component.selectedItem as T? },
              { component, value -> component.setSelectedItem(value) },
              prop)
}

@Deprecated("Please, recompile code", level = DeprecationLevel.HIDDEN)
@ApiStatus.ScheduledForRemoval
fun <T, C : ComboBox<T>> Cell<C>.bindItem(property: GraphProperty<T>) = bindItem(property)

fun <T, C : ComboBox<T>> Cell<C>.bindItem(property: ObservableMutableProperty<T>): Cell<C> {
  installValidationRequestor(property)
  return applyToComponent { bind(property) }
}

/**
 * If ComboBox doesn't have any items, then NPE will be thrown. Because of that the method is deprecated now and will be changed in
 * the future. What to do:
 *
 * 1. If the property is nullable, use [bindItemNullable]
 * 2. If the property is non-nullable:
 *     * If the ComboBox is not empty use `bindItem(::prop.toNullableProperty())
 *     * In other cases other approaches should be used depending on desired behaviour
 */
@Deprecated("Signature of the method is going to be changed to bindItem(prop: KMutableProperty0<T?>). See the doc for details")
fun <T, C : ComboBox<T>> Cell<C>.bindItem(prop: KMutableProperty0<T>): Cell<C> {
  return bindItem(prop.toMutableProperty().toNullableProperty())
}

fun <T, C : ComboBox<T>> Cell<C>.bindItemNullable(prop: KMutableProperty0<T?>): Cell<C> {
  return bindItem(prop.toMutableProperty())
}

fun <T, C : ComboBox<T>> Cell<C>.bindItem(getter: () -> T?, setter: (T?) -> Unit): Cell<C> {
  return bindItem(MutableProperty(getter, setter))
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
