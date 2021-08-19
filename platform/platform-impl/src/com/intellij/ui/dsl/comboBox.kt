// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.layout.*
import kotlin.reflect.KMutableProperty0

fun <T> Cell<ComboBox<T>>.bindItem(binding: PropertyBinding<T?>): Cell<ComboBox<T>> {
  component.selectedItem = binding.get()
  return bind({ component -> component.selectedItem as T? },
    { component, value -> component.setSelectedItem(value) },
    binding)
}

fun <T> Cell<ComboBox<T>>.bindItem(property: GraphProperty<T>): Cell<ComboBox<T>> {
  component.selectedItem = property.get()
  return graphProperty(property)
    .applyToComponent { bind(property) }
}

inline fun <reified T : Any> Cell<ComboBox<T>>.bindItem(prop: KMutableProperty0<T>): Cell<ComboBox<T>> {
  return bindItem(prop.toBinding().toNullable())
}

fun <T> Cell<ComboBox<T>>.bindItem(getter: () -> T?, setter: (T?) -> Unit): Cell<ComboBox<T>> {
  return bindItem(PropertyBinding(getter, setter))
}

fun <T> Cell<ComboBox<T>>.columns(columns: Int): Cell<ComboBox<T>> {
  // See JTextField.getColumnWidth implementation
  val columnWidth = component.getFontMetrics(component.font).charWidth('m')
  val insets = component.insets
  component.setMinimumAndPreferredWidth(columns * columnWidth + insets.left + insets.right)
  return this
}
