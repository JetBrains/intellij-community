// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.layout.*
import kotlin.reflect.KMutableProperty0

fun <T : TextFieldWithBrowseButton> Cell<T>.columns(columns: Int): Cell<T> {
  component.textField.columns = columns
  return this
}

fun <T : TextFieldWithBrowseButton> Cell<T>.bindText(binding: PropertyBinding<String>): Cell<T> {
  return bind(TextFieldWithBrowseButton::getText, TextFieldWithBrowseButton::setText, binding)
}

fun <T : TextFieldWithBrowseButton> Cell<T>.bindText(prop: KMutableProperty0<String>): Cell<T> {
  return bindText(prop.toBinding())
}

fun <T : TextFieldWithBrowseButton> Cell<T>.bindText(getter: () -> String, setter: (String) -> Unit): Cell<T> {
  return bindText(PropertyBinding(getter, setter))
}
