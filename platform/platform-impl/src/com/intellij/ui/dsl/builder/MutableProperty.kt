// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.ui.dsl.builder.impl.MutablePropertyImpl
import kotlin.reflect.KMutableProperty0

interface MutableProperty<T> {

  fun get(): T
  fun set(value: T)

}

@Suppress("FunctionName")
fun <T> MutableProperty(getter: () -> T, setter: (value: T) -> Unit): MutableProperty<T> {
  return MutablePropertyImpl(getter, setter)
}

fun <T> KMutableProperty0<T>.toMutableProperty(): MutableProperty<T> {
  return MutableProperty({ get() }, { set(it) })
}

/**
 * Converts property to nullable MutableProperty. Use this method if there is no chance null is set into resulting [MutableProperty],
 * otherwise NPE will be thrown. See also safe overloaded [toNullableProperty] method with default value.
 *
 * Useful for [Cell<ComboBox>.bindItem(prop: KMutableProperty0<T?>)] if the ComboBox is not empty and the property is non-nullable
 */
fun <T> KMutableProperty0<T>.toNullableProperty(): MutableProperty<T?> {
  return MutableProperty({ get() }, { set(it!!) })
}

fun <T> KMutableProperty0<T>.toNullableProperty(defaultValue: T): MutableProperty<T?> {
  return MutableProperty({ get() }, { set(it ?: defaultValue) })
}

/**
 * See the doc for overloaded method
 */
fun <T> MutableProperty<T>.toNullableProperty(): MutableProperty<T?> {
  return MutableProperty({ get() }, { set(it!!) })
}

fun <T> MutableProperty<T>.toNullableProperty(defaultValue: T): MutableProperty<T?> {
  return MutableProperty({ get() }, { set(it ?: defaultValue) })
}
