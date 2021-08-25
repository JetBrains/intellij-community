// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.ui.layout.*
import javax.swing.JTextField
import javax.swing.text.JTextComponent
import kotlin.reflect.KMutableProperty0

fun <T : JTextComponent> Cell<T>.bindText(binding: PropertyBinding<String>): Cell<T> {
  component.text = binding.get()
  return bind(JTextComponent::getText, JTextComponent::setText, binding)
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

fun <T : JTextComponent> Cell<T>.bindIntText(prop: KMutableProperty0<Int>): Cell<T> {
  return bindIntText(prop.toBinding())
}

fun <T : JTextComponent> Cell<T>.bindIntText(getter: () -> Int, setter: (Int) -> Unit): Cell<T> {
  return bindIntText(PropertyBinding(getter, setter))
}

fun <T : JTextField> Cell<T>.columns(columns: Int): Cell<T> {
  component.columns = columns
  return this
}
