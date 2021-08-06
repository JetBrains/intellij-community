// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.AbstractButton
import javax.swing.JComponent
import kotlin.reflect.KMutableProperty0

@ApiStatus.Experimental
interface CellBuilder<T : JComponent> : CellBuilderBase<CellBuilder<T>> {

  fun applyToComponent(task: T.() -> Unit): CellBuilder<T>

  fun enabled(isEnabled: Boolean): CellBuilder<T>

  /**
   * If this method is called, the value of the component will be stored to the backing property only if the component is enabled
   */
  fun applyIfEnabled(): CellBuilder<T>

  fun <V> bind(componentGet: (T) -> V, componentSet: (T, V) -> Unit, modelBinding: PropertyBinding<V>): CellBuilder<T>
}

fun <T : AbstractButton> CellBuilder<T>.bind(modelBinding: PropertyBinding<Boolean>): CellBuilder<T> {
  return bind(AbstractButton::isSelected, AbstractButton::setSelected, modelBinding)
}

fun <T : AbstractButton> CellBuilder<T>.bind(prop: KMutableProperty0<Boolean>): CellBuilder<T> {
  return bind(prop.toBinding())
}

fun <T : AbstractButton> CellBuilder<T>.bind(getter: () -> Boolean, setter: (Boolean) -> Unit): CellBuilder<T> {
  return bind(PropertyBinding(getter, setter))
}
