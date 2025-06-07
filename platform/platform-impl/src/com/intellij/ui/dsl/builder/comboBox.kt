// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.impl.CellImpl.Companion.installValidationRequestor
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComboBox
import kotlin.reflect.KMutableProperty0
import com.intellij.openapi.observable.util.whenItemChangedFromUi as whenItemChangedFromUiImpl
import com.intellij.openapi.observable.util.whenItemSelectedFromUi as whenItemSelectedFromUiImpl

fun <T, C : ComboBox<T>> Cell<C>.bindItem(prop: MutableProperty<T?>): Cell<C> {
  return bind({ component -> component.selectedItem as T? },
              { component, value -> component.setSelectedItem(value) },
              prop)
}

fun <T, C : ComboBox<T>> Cell<C>.bindItem(property: ObservableMutableProperty<T>): Cell<C> {
  installValidationRequestor(property)
  return applyToComponent { bind(property) }
}

/**
 * If the ComboBox is not empty and non-nullable property is used then
 * the following code can be used: `bindItem(::prop.toNullableProperty())`
 */
fun <T, C : ComboBox<T>> Cell<C>.bindItem(prop: KMutableProperty0<T?>): Cell<C> {
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
fun <T, C : ComboBox<T>> Cell<C>.columns(columns: Int): Cell<C> = apply {
  component.columns(columns)
}

fun <T, C : ComboBox<T>> C.columns(columns: Int): C = apply {
  // See JTextField.getColumnWidth implementation
  val columnWidth = getFontMetrics(font).charWidth('m')
  setMinimumAndPreferredWidth(columns * columnWidth + insets.left + insets.right)
}

@ApiStatus.Experimental
fun <T, C : JComboBox<T>> Cell<C>.whenItemSelectedFromUi(parentDisposable: Disposable? = null, listener: (T) -> Unit): Cell<C> {
  return applyToComponent { whenItemSelectedFromUiImpl(parentDisposable, listener) }
}

@ApiStatus.Experimental
fun <T, C : JComboBox<T>> Cell<C>.whenItemChangedFromUi(parentDisposable: Disposable? = null, listener: (T?) -> Unit): Cell<C> {
  return applyToComponent { whenItemChangedFromUiImpl(parentDisposable, listener) }
}
