// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.text.JTextComponent
import kotlin.reflect.KMutableProperty0

internal const val DSL_INT_TEXT_RANGE_PROPERTY = "dsl.intText.range"

@ApiStatus.Experimental
interface CellBuilder<out T : JComponent> : CellBuilderBase<CellBuilder<T>> {

  override fun horizontalAlign(horizontalAlign: HorizontalAlign): CellBuilder<T>

  override fun verticalAlign(verticalAlign: VerticalAlign): CellBuilder<T>

  override fun resizableColumn(): CellBuilder<T>

  override fun comment(comment: String, maxLineLength: Int): CellBuilder<T>

  override fun gap(rightGap: RightGap): CellBuilder<T>

  val component: T

  fun applyToComponent(task: T.() -> Unit): CellBuilder<T>

  fun enabled(isEnabled: Boolean): CellBuilder<T>

  fun visibleIf(predicate: ComponentPredicate): CellBuilder<T>

  /**
   * If this method is called, the value of the component will be stored to the backing property only if the component is enabled
   */
  fun applyIfEnabled(): CellBuilder<T>

  fun <V> bind(componentGet: (T) -> V, componentSet: (T, V) -> Unit, binding: PropertyBinding<V>): CellBuilder<T>
}

fun <T : AbstractButton> CellBuilder<T>.bindSelected(binding: PropertyBinding<Boolean>): CellBuilder<T> {
  return bind(AbstractButton::isSelected, AbstractButton::setSelected, binding)
}

fun <T : AbstractButton> CellBuilder<T>.bindSelected(prop: KMutableProperty0<Boolean>): CellBuilder<T> {
  return bindSelected(prop.toBinding())
}

fun <T : AbstractButton> CellBuilder<T>.bindSelected(getter: () -> Boolean, setter: (Boolean) -> Unit): CellBuilder<T> {
  return bindSelected(PropertyBinding(getter, setter))
}

fun <T : AbstractButton> CellBuilder<T>.actionListener(actionListener: (event: ActionEvent, component: T) -> Unit): CellBuilder<T> {
  component.addActionListener(ActionListener { actionListener(it, component) })
  return this
}

fun <T : JTextComponent> CellBuilder<T>.bindText(binding: PropertyBinding<String>): CellBuilder<T> {
  component.text = binding.get()
  return bind(JTextComponent::getText, JTextComponent::setText, binding)
}

fun <T : JTextComponent> CellBuilder<T>.bindText(prop: KMutableProperty0<String>): CellBuilder<T> {
  return bindText(prop.toBinding())
}

fun <T : JTextComponent> CellBuilder<T>.bindText(getter: () -> String, setter: (String) -> Unit): CellBuilder<T> {
  return bindText(PropertyBinding(getter, setter))
}

fun <T : JTextComponent> CellBuilder<T>.bindIntText(binding: PropertyBinding<Int>): CellBuilder<T> {
  val range = component.getClientProperty(DSL_INT_TEXT_RANGE_PROPERTY) as? IntRange
  return bindText({ binding.get().toString() },
                  { value ->
                    value.toIntOrNull()?.let { intValue ->
                      binding.set(range?.let { intValue.coerceIn(it.first, it.last) } ?: intValue)
                    }
                  })
}

fun <T : JTextComponent> CellBuilder<T>.bindIntText(prop: KMutableProperty0<Int>): CellBuilder<T> {
  return bindIntText(prop.toBinding())
}

fun <T : JTextComponent> CellBuilder<T>.bindIntText(getter: () -> Int, setter: (Int) -> Unit): CellBuilder<T> {
  return bindIntText(PropertyBinding(getter, setter))
}

fun <T> CellBuilder<ComboBox<T>>.bindItem(binding: PropertyBinding<T?>): CellBuilder<ComboBox<T>> {
  component.selectedItem = binding.get()
  return bind({ component -> component.selectedItem as T? },
              { component, value -> component.setSelectedItem(value) },
              binding)
}

/* todo
fun <T> CellBuilder<ComboBox<T>>.bindItem(prop: KMutableProperty0<T>): CellBuilder<ComboBox<T>> {
  return bindItem(prop.toBinding().toNullable())
}
*/

fun <T> CellBuilder<ComboBox<T>>.bindItem(getter: () -> T?, setter: (T?) -> Unit): CellBuilder<ComboBox<T>> {
  return bindItem(PropertyBinding(getter, setter))
}
