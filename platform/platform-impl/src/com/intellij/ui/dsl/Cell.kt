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
import javax.swing.JTextField
import javax.swing.text.JTextComponent
import kotlin.reflect.KMutableProperty0

const val COLUMNS_LARGE = 25

internal const val DSL_INT_TEXT_RANGE_PROPERTY = "dsl.intText.range"

@ApiStatus.Experimental
interface Cell<out T : JComponent> : CellBase<Cell<T>> {

  override fun horizontalAlign(horizontalAlign: HorizontalAlign): Cell<T>

  override fun verticalAlign(verticalAlign: VerticalAlign): Cell<T>

  override fun resizableColumn(): Cell<T>

  override fun comment(comment: String, maxLineLength: Int): Cell<T>

  override fun gap(rightGap: RightGap): Cell<T>

  val component: T

  fun applyToComponent(task: T.() -> Unit): Cell<T>

  override fun enabled(isEnabled: Boolean): Cell<T>

  fun enableIf(predicate: ComponentPredicate): Cell<T>

  override fun visible(isVisible: Boolean): Cell<T>

  fun visibleIf(predicate: ComponentPredicate): Cell<T>

  /**
   * If this method is called, the value of the component will be stored to the backing property only if the component is enabled
   */
  fun applyIfEnabled(): Cell<T>

  fun <V> bind(componentGet: (T) -> V, componentSet: (T, V) -> Unit, binding: PropertyBinding<V>): Cell<T>

  fun onApply(callback: () -> Unit): Cell<T>

  fun onReset(callback: () -> Unit): Cell<T>

  fun onIsModified(callback: () -> Boolean): Cell<T>
}

fun <T : AbstractButton> Cell<T>.bindSelected(binding: PropertyBinding<Boolean>): Cell<T> {
  component.isSelected = binding.get()
  return bind(AbstractButton::isSelected, AbstractButton::setSelected, binding)
}

fun <T : AbstractButton> Cell<T>.bindSelected(prop: KMutableProperty0<Boolean>): Cell<T> {
  return bindSelected(prop.toBinding())
}

fun <T : AbstractButton> Cell<T>.bindSelected(getter: () -> Boolean, setter: (Boolean) -> Unit): Cell<T> {
  return bindSelected(PropertyBinding(getter, setter))
}

fun <T : AbstractButton> Cell<T>.actionListener(actionListener: (event: ActionEvent, component: T) -> Unit): Cell<T> {
  component.addActionListener(ActionListener { actionListener(it, component) })
  return this
}

val Cell<AbstractButton>.selected
  get() = component.selected

fun <T : JTextComponent> Cell<T>.bindText(binding: PropertyBinding<String>): Cell<T> {
  component.text = binding.get()
  return bind(JTextComponent::getText, JTextComponent::setText, binding)
}

fun <T : JTextComponent> Cell<T>.bindText(prop: KMutableProperty0<String>): Cell<T> {
  return bindText(prop.toBinding())
}

fun <T : JTextComponent> Cell<T>.bindText(getter: () -> String, setter: (String) -> Unit): Cell<T> {
  return bindText(PropertyBinding(getter, setter))
}

fun <T : JTextComponent> Cell<T>.bindIntText(binding: PropertyBinding<Int>): Cell<T> {
  val range = component.getClientProperty(DSL_INT_TEXT_RANGE_PROPERTY) as? IntRange
  return bindText({ binding.get().toString() },
    { value ->
      value.toIntOrNull()?.let { intValue ->
        binding.set(range?.let { intValue.coerceIn(it.first, it.last) } ?: intValue)
      }
    })
}

fun <T : JTextComponent> Cell<T>.bindIntText(prop: KMutableProperty0<Int>): Cell<T> {
  return bindIntText(prop.toBinding())
}

fun <T : JTextComponent> Cell<T>.bindIntText(getter: () -> Int, setter: (Int) -> Unit): Cell<T> {
  return bindIntText(PropertyBinding(getter, setter))
}

fun <T : JTextField> Cell<T>.columns(columns: Int): Cell<T> {
  component.columns = columns
  return this
}

fun <T> Cell<ComboBox<T>>.bindItem(binding: PropertyBinding<T?>): Cell<ComboBox<T>> {
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

fun <T> Cell<ComboBox<T>>.bindItem(getter: () -> T?, setter: (T?) -> Unit): Cell<ComboBox<T>> {
  return bindItem(PropertyBinding(getter, setter))
}
