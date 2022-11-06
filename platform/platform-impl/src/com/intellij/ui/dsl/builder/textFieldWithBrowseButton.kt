// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.validation.DialogValidation
import com.intellij.openapi.ui.validation.forTextFieldWithBrowseButton
import com.intellij.openapi.ui.validation.trimParameter
import com.intellij.ui.dsl.builder.impl.CellImpl.Companion.installValidationRequestor
import com.intellij.util.containers.map2Array
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KMutableProperty0
import com.intellij.openapi.observable.util.whenTextChangedFromUi as whenTextChangedFromUiImpl

/**
 * Minimal width of text field in chars
 *
 * @see COLUMNS_TINY
 * @see COLUMNS_SHORT
 * @see COLUMNS_MEDIUM
 * @see COLUMNS_LARGE
 */
fun <T : TextFieldWithBrowseButton> Cell<T>.columns(columns: Int): Cell<T> {
  component.textField.columns = columns
  return this
}

fun <T : TextFieldWithBrowseButton> Cell<T>.bindText(property: ObservableMutableProperty<String>): Cell<T> {
  installValidationRequestor(property)
  return applyToComponent { bind(property) }
}

fun <T : TextFieldWithBrowseButton> Cell<T>.bindText(prop: KMutableProperty0<String>): Cell<T> {
  return bindText(prop.toMutableProperty())
}

fun <T : TextFieldWithBrowseButton> Cell<T>.bindText(getter: () -> String, setter: (String) -> Unit): Cell<T> {
  return bindText(MutableProperty(getter, setter))
}

fun <T : TextFieldWithBrowseButton> Cell<T>.bindText(prop: MutableProperty<String>): Cell<T> {
  return bind(TextFieldWithBrowseButton::getText, TextFieldWithBrowseButton::setText, prop)
}

fun <T : TextFieldWithBrowseButton> Cell<T>.text(text: String): Cell<T> {
  component.text = text
  return this
}

fun <T : TextFieldWithBrowseButton> Cell<T>.trimmedTextValidation(vararg validations: DialogValidation.WithParameter<() -> String>) =
  textValidation(*validations.map2Array { it.trimParameter() })

fun <T : TextFieldWithBrowseButton> Cell<T>.textValidation(vararg validations: DialogValidation.WithParameter<() -> String>) =
  validation(*validations.map2Array { it.forTextFieldWithBrowseButton() })

@ApiStatus.Experimental
fun <T : TextFieldWithBrowseButton> Cell<T>.whenTextChangedFromUi(parentDisposable: Disposable? = null, listener: (String) -> Unit): Cell<T> {
  return applyToComponent { whenTextChangedFromUiImpl(parentDisposable, listener) }
}