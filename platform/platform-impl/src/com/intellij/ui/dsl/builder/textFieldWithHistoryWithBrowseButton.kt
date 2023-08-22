// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.ui.TextFieldWithHistoryWithBrowseButton
import kotlin.reflect.KMutableProperty0

fun <T : TextFieldWithHistoryWithBrowseButton> Cell<T>.bindText(prop: KMutableProperty0<String>): Cell<T> {
  return bindText(prop.toMutableProperty())
}

fun <T : TextFieldWithHistoryWithBrowseButton> Cell<T>.bindText(getter: () -> String, setter: (String) -> Unit): Cell<T> {
  return bindText(MutableProperty(getter, setter))
}

fun <T : TextFieldWithHistoryWithBrowseButton> Cell<T>.bindText(prop: MutableProperty<String>): Cell<T> {
  return bind(TextFieldWithHistoryWithBrowseButton::getText, TextFieldWithHistoryWithBrowseButton::setText, prop)
}

fun <T : TextFieldWithHistoryWithBrowseButton> Cell<T>.text(text: String): Cell<T> {
  component.text = text
  return this
}
