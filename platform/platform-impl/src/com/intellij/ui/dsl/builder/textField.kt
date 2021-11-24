// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableClearableProperty
import com.intellij.openapi.observable.properties.transform
import com.intellij.openapi.ui.whenTextModified
import com.intellij.ui.dsl.ValidationException
import com.intellij.ui.dsl.catchValidationException
import com.intellij.ui.dsl.stringToInt
import com.intellij.ui.dsl.validateIntInRange
import com.intellij.ui.layout.*
import com.intellij.util.lockOrSkip
import java.util.concurrent.atomic.AtomicBoolean
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
  return bindText({ binding.get().toString() },
                  { value -> catchValidationException { binding.set(component.getValidatedIntValue(value)) } })
}

fun <T : JTextComponent> Cell<T>.bindIntText(property: GraphProperty<Int>): Cell<T> {
  component.text = property.get().toString()
  return graphProperty(property)
    .applyToComponent {
      bind(property.transform({ it.toString() }, { component.getValidatedIntValue(it) }))
    }
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

@Throws(ValidationException::class)
private fun JTextComponent.getValidatedIntValue(value: String): Int {
  val result = stringToInt(value)
  val range = getClientProperty(DSL_INT_TEXT_RANGE_PROPERTY) as? IntRange
  range?.let { validateIntInRange(result, it) }
  return result
}

private fun JTextComponent.bind(property: ObservableClearableProperty<String>) {
  // See: IDEA-238573 removed cyclic update of UI components that bound with properties
  val mutex = AtomicBoolean()
  property.afterChange {
    mutex.lockOrSkip {
      text = it
    }
  }
  whenTextModified {
    mutex.lockOrSkip {
      // Catch transformed GraphProperties, e.g. for intTextField
      catchValidationException {
        property.set(text)
      }
    }
  }
}
