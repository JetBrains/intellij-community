// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.ui.JBIntSpinner
import com.intellij.ui.dsl.builder.impl.toBindingInternal
import com.intellij.ui.layout.*
import javax.swing.JSpinner
import kotlin.reflect.KMutableProperty0

fun <T : JBIntSpinner> Cell<T>.bindIntValue(binding: PropertyBinding<Int>): Cell<T> {
  return bind(JBIntSpinner::getNumber, JBIntSpinner::setNumber, binding)
}

fun <T : JBIntSpinner> Cell<T>.bindIntValue(prop: KMutableProperty0<Int>): Cell<T> {
  return bindIntValue(prop.toBindingInternal())
}

fun <T : JBIntSpinner> Cell<T>.bindIntValue(getter: () -> Int, setter: (Int) -> Unit): Cell<T> {
  return bindIntValue(PropertyBinding(getter, setter))
}

fun <T : JSpinner> Cell<T>.bindValue(binding: PropertyBinding<Double>): Cell<T> {
  return bind({ it.value as Double }, JSpinner::setValue, binding)
}

fun <T : JSpinner> Cell<T>.bindValue(prop: KMutableProperty0<Double>): Cell<T> {
  return bindValue(prop.toBindingInternal())
}

fun <T : JSpinner> Cell<T>.bindValue(getter: () -> Double, setter: (Double) -> Unit): Cell<T> {
  return bindValue(PropertyBinding(getter, setter))
}
