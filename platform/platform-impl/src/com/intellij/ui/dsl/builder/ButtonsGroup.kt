// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.ui.dsl.builder.impl.toBindingInternal
import com.intellij.ui.layout.*
import kotlin.reflect.KMutableProperty0

interface ButtonsGroup {

  /**
   * Binds values of radio buttons (must be provided for each radio button in [Row.radioButton]) inside the buttons group with [binding].
   * This method should be used in rare cases, use extension methods with the same name instead
   */
  fun <T> bind(binding: PropertyBinding<T>, type: Class<T>): ButtonsGroup
}

inline fun <reified T : Any> ButtonsGroup.bind(noinline getter: () -> T, noinline setter: (T) -> Unit): ButtonsGroup {
  return bind(PropertyBinding(getter, setter))
}

inline fun <reified T : Any> ButtonsGroup.bind(prop: KMutableProperty0<T>): ButtonsGroup {
  return bind(prop.toBindingInternal())
}

inline fun <reified T : Any> ButtonsGroup.bind(binding: PropertyBinding<T>): ButtonsGroup {
  return bind(binding, T::class.java)
}
