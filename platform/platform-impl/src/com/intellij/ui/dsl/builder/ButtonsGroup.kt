// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KMutableProperty0

@ApiStatus.Experimental
interface ButtonsGroup {

  /**
   * Binds values of radio buttons (must be provided for each radio button in [Row.radioButton]) inside the buttons group with [binding].
   * This method should be used in rare cases, use extension methods with the same name instead
   */
  fun <T> bind(binding: PropertyBinding<T>, type: Class<T>)
}

inline fun <reified T : Any> ButtonsGroup.bind(noinline getter: () -> T, noinline setter: (T) -> Unit) {
  bind(PropertyBinding(getter, setter))
}

inline fun <reified T : Any> ButtonsGroup.bind(prop: KMutableProperty0<T>) {
  bind(prop.toBinding())
}

inline fun <reified T : Any> ButtonsGroup.bind(binding: PropertyBinding<T>) {
  bind(binding, T::class.java)
}
