// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.AbstractButton
import javax.swing.JComponent
import kotlin.reflect.KMutableProperty0

@ApiStatus.Experimental
interface CellBuilder<T : JComponent> : CellBuilderBase<CellBuilder<T>> {

  override fun horizontalAlign(horizontalAlign: HorizontalAlign): CellBuilder<T>

  override fun verticalAlign(verticalAlign: VerticalAlign): CellBuilder<T>

  override fun resizableColumn(): CellBuilder<T>

  override fun comment(comment: String, maxLineLength: Int): CellBuilder<T>

  override fun rightLabelGap(): CellBuilder<T>

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

fun <T : AbstractButton> CellBuilder<T>.actionListener(actionListener: (event: ActionEvent, component: T) -> Unit): CellBuilder<T> {
  return applyToComponent { addActionListener(ActionListener { actionListener(it, this) }) }
}
