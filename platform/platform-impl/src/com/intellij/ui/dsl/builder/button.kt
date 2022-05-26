// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.bind
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.impl.CellImpl.Companion.installValidationRequestor
import com.intellij.ui.dsl.builder.impl.toBindingInternal
import com.intellij.ui.layout.*
import com.intellij.util.ui.ThreeStateCheckBox
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.AbstractButton
import kotlin.reflect.KMutableProperty0

fun <T : AbstractButton> Cell<T>.bindSelected(binding: PropertyBinding<Boolean>): Cell<T> {
  return bind(AbstractButton::isSelected, AbstractButton::setSelected, binding)
}

@Deprecated("Please, recompile code", level = DeprecationLevel.HIDDEN)
@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
fun <T : JBCheckBox> Cell<T>.bindSelected(property: GraphProperty<Boolean>) = bindSelected(property)

@Deprecated("Please, recompile code", level = DeprecationLevel.HIDDEN)
@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
fun <T : ThreeStateCheckBox> Cell<T>.bindState(property: GraphProperty<ThreeStateCheckBox.State>) = bindState(property)

fun <T : JBCheckBox> Cell<T>.bindSelected(property: ObservableMutableProperty<Boolean>): Cell<T> {
  installValidationRequestor(property)
  return applyToComponent { bind(property) }
}

fun <T : ThreeStateCheckBox> Cell<T>.bindState(property: ObservableMutableProperty<ThreeStateCheckBox.State>): Cell<T> {
  installValidationRequestor(property)
  return applyToComponent { bind(property) }
}

fun <T : AbstractButton> Cell<T>.bindSelected(prop: KMutableProperty0<Boolean>): Cell<T> {
  return bindSelected(prop.toBindingInternal())
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
