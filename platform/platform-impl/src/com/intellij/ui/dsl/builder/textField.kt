// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.ui.layout.*
import javax.swing.JTextField
import javax.swing.text.JTextComponent
import kotlin.reflect.KMutableProperty0

/**
 * Columns for text components with tiny width. Used for [Row.intTextField] by default
 */
const val COLUMNS_TINY = 6

/**
 * Columns for text components with short width (used instead of deprecated [GrowPolicy.SHORT_TEXT])
 */
const val COLUMNS_SHORT = 18

/**
 * Columns for text components with medium width (used instead of deprecated [GrowPolicy.MEDIUM_TEXT])
 */
const val COLUMNS_MEDIUM = 25

const val COLUMNS_LARGE = 36

fun <T : JTextComponent> Cell<T>.bindText(binding: PropertyBinding<String>): Cell<T> {
  return bind(JTextComponent::getText, JTextComponent::setText, binding)
}

fun <T : JTextComponent> Cell<T>.bindText(property: GraphProperty<String>): Cell<T> {
  component.text = property.get()
  return graphProperty(property)
    .applyToComponent { bind(property) }
}

fun <T : JTextComponent> Cell<T>.bindText(prop: KMutableProperty0<String>): Cell<T> {
  return bindText(prop.toBinding())
}

fun <T : JTextComponent> Cell<T>.bindText(getter: () -> String, setter: (String) -> Unit): Cell<T> {
  return bindText(PropertyBinding(getter, setter))
}

fun <T : JTextComponent> Cell<T>.bindIntText(binding: PropertyBinding<Int>): Cell<T> {
  val range = component.getClientProperty(DSL_INT_TEXT_RANGE_PROPERTY) as? IntRange
  return bindText({ binding.get().toString() },
    { value ->
      value.toIntOrNull()?.let { intValue ->
        binding.set(range?.let { intValue.coerceIn(it.first, it.last) } ?: intValue)
      }
    })
}

fun <T : JTextComponent> Cell<T>.bindIntText(property: GraphProperty<Int>): Cell<T> {
  component.text = property.get().toString()
  return graphProperty(property)
    .applyToComponent { bindIntProperty(property) }
}

fun <T : JTextComponent> Cell<T>.bindIntText(prop: KMutableProperty0<Int>): Cell<T> {
  return bindIntText(prop.toBinding())
}

fun <T : JTextComponent> Cell<T>.bindIntText(getter: () -> Int, setter: (Int) -> Unit): Cell<T> {
  return bindIntText(PropertyBinding(getter, setter))
}

fun <T : JTextComponent> Cell<T>.text(text: String): Cell<T> {
  component.text = text
  return this
}

/**
 * Minimal width of text field in chars
 *
 * @see COLUMNS_TINY
 * @see COLUMNS_SHORT
 * @see COLUMNS_MEDIUM
 * @see COLUMNS_LARGE
 */
fun <T : JTextField> Cell<T>.columns(columns: Int) = apply {
  component.columns(columns)
}

fun <T : JTextField> T.columns(columns: Int) = apply {
  this.columns = columns
}
