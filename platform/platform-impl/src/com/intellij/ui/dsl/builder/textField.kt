// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.*
import com.intellij.openapi.ui.validation.DialogValidation
import com.intellij.openapi.ui.validation.forTextComponent
import com.intellij.openapi.ui.validation.trimParameter
import com.intellij.ui.dsl.ValidationException
import com.intellij.ui.dsl.builder.impl.CellImpl.Companion.installValidationRequestor
import com.intellij.ui.dsl.builder.impl.DslComponentPropertyInternal
import com.intellij.ui.dsl.catchValidationException
import com.intellij.ui.dsl.stringToInt
import com.intellij.ui.dsl.validateIntInRange
import com.intellij.ui.layout.*
import com.intellij.util.containers.map2Array
import org.jetbrains.annotations.ApiStatus
import javax.swing.JTextField
import javax.swing.text.JTextComponent
import kotlin.reflect.KMutableProperty0
import com.intellij.openapi.observable.util.whenTextChangedFromUi as whenTextChangedFromUiImpl

/**
 * Columns for text components with tiny width. Used for [Row.intTextField] by default
 */
const val COLUMNS_TINY = 6

/**
 * Columns for text components with short width
 */
const val COLUMNS_SHORT = 18

/**
 * Columns for text components with medium width
 */
const val COLUMNS_MEDIUM = 25

const val COLUMNS_LARGE = 36

fun <T : JTextComponent> Cell<T>.bindText(property: ObservableMutableProperty<String>): Cell<T> {
  installValidationRequestor(property)
  return applyToComponent { bind(property) }
}

fun <T : JTextComponent> Cell<T>.bindText(prop: KMutableProperty0<String>): Cell<T> {
  return bindText(prop.toMutableProperty())
}

fun <T : JTextComponent> Cell<T>.bindText(getter: () -> String, setter: (String) -> Unit): Cell<T> {
  return bindText(MutableProperty(getter, setter))
}

fun <T : JTextComponent> Cell<T>.bindText(prop: MutableProperty<String>): Cell<T> {
  return bind(JTextComponent::getText, JTextComponent::setText, prop)
}

fun <T : JTextComponent> Cell<T>.bindIntText(property: ObservableMutableProperty<Int>): Cell<T> {
  installValidationRequestor(property)
  val stringProperty = property
    .backwardFilter { component.isIntInRange(it) }
    .toStringIntProperty()
  return applyToComponent {
    bind(stringProperty)
  }
}

fun <T : JTextComponent> Cell<T>.bindIntText(prop: MutableProperty<Int>): Cell<T> {
  return bindText({ prop.get().toString() },
                  { value -> catchValidationException { prop.set(component.getValidatedIntValue(value)) } })
}

fun <T : JTextComponent> Cell<T>.bindIntText(prop: KMutableProperty0<Int>): Cell<T> {
  return bindIntText(prop.toMutableProperty())
}

fun <T : JTextComponent> Cell<T>.bindIntText(getter: () -> Int, setter: (Int) -> Unit): Cell<T> {
  return bindIntText(MutableProperty(getter, setter))
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
  val range = getClientProperty(DslComponentPropertyInternal.INT_TEXT_RANGE) as IntRange?
  range?.let { validateIntInRange(result, it) }
  return result
}

private fun JTextComponent.isIntInRange(value: Int): Boolean {
  val range = getClientProperty(DslComponentPropertyInternal.INT_TEXT_RANGE) as IntRange?
  return range == null || value in range
}

fun <T : JTextComponent> Cell<T>.trimmedTextValidation(vararg validations: DialogValidation.WithParameter<() -> String>) =
  textValidation(*validations.map2Array { it.trimParameter() })

fun <T : JTextComponent> Cell<T>.textValidation(vararg validations: DialogValidation.WithParameter<() -> String>) =
  validation(*validations.map2Array { it.forTextComponent() })

@ApiStatus.Experimental
fun <T: JTextComponent> Cell<T>.whenTextChangedFromUi(parentDisposable: Disposable? = null, listener: (String) -> Unit): Cell<T> {
  return applyToComponent { whenTextChangedFromUiImpl(parentDisposable, listener) }
}