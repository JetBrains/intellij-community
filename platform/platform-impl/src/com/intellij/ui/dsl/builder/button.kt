// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.bind
import com.intellij.ui.dsl.builder.impl.CellImpl.Companion.installValidationRequestor
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.selected
import com.intellij.util.ui.ThreeStateCheckBox
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.AbstractButton
import javax.swing.JCheckBox
import javax.swing.JToggleButton
import kotlin.reflect.KMutableProperty0
import com.intellij.openapi.observable.util.whenStateChangedFromUi as whenStateChangedFromUiImpl

fun <T : JToggleButton> Cell<T>.bindSelected(property: ObservableMutableProperty<Boolean>): Cell<T> {
  installValidationRequestor(property)
  return applyToComponent { bind(property) }
}

fun <T : ThreeStateCheckBox> Cell<T>.bindState(property: ObservableMutableProperty<ThreeStateCheckBox.State>): Cell<T> {
  installValidationRequestor(property)
  return applyToComponent { bind(property) }
}

fun <T : AbstractButton> Cell<T>.bindSelected(prop: MutableProperty<Boolean>): Cell<T> {
  return bind(AbstractButton::isSelected, AbstractButton::setSelected, prop)
}

fun <T : AbstractButton> Cell<T>.bindSelected(prop: KMutableProperty0<Boolean>): Cell<T> {
  return bindSelected(prop.toMutableProperty())
}

fun <T : AbstractButton> Cell<T>.bindSelected(getter: () -> Boolean, setter: (Boolean) -> Unit): Cell<T> {
  return bindSelected(MutableProperty(getter, setter))
}

fun <T : AbstractButton> Cell<T>.selected(value: Boolean): Cell<T> {
  component.isSelected = value
  return this
}

fun <T : AbstractButton> Cell<T>.actionListener(actionListener: (event: ActionEvent, component: T) -> Unit): Cell<T> {
  component.addActionListener(ActionListener { actionListener(it, component) })
  return this
}

val Cell<AbstractButton>.selected: ComponentPredicate
  get() = component.selected

@ApiStatus.Experimental
fun <T : JCheckBox> Cell<T>.whenStateChangedFromUi(parentDisposable: Disposable? = null, listener: (Boolean) -> Unit): Cell<T> {
  return applyToComponent { whenStateChangedFromUiImpl(parentDisposable, listener) }
}