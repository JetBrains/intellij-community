// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KMutableProperty0

@ApiStatus.NonExtendable
interface ButtonsGroup: RowsRange {

  override fun visible(isVisible: Boolean): ButtonsGroup

  override fun visibleIf(predicate: ComponentPredicate): ButtonsGroup

  override fun enabled(isEnabled: Boolean): ButtonsGroup

  override fun enabledIf(predicate: ComponentPredicate): ButtonsGroup

  /**
   * Binds values of radio buttons (must be provided for each radio button in [Row.radioButton]) inside the buttons group with [prop].
   * This method should be used in rare cases, use extension methods with the same name instead
   */
  fun <T> bind(prop: MutableProperty<T>, type: Class<T>): ButtonsGroup
}

inline fun <reified T : Any> ButtonsGroup.bind(noinline getter: () -> T, noinline setter: (T) -> Unit): ButtonsGroup {
  return bind(MutableProperty(getter, setter), T::class.java)
}

inline fun <reified T : Any> ButtonsGroup.bind(prop: KMutableProperty0<T>): ButtonsGroup {
  return bind(prop.toMutableProperty(), T::class.java)
}

inline fun <reified T : Any> ButtonsGroup.bind(prop: MutableProperty<T>): ButtonsGroup {
  return bind(prop, T::class.java)
}
