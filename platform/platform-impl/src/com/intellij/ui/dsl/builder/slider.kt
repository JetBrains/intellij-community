// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import java.util.*
import javax.swing.JComponent
import javax.swing.JSlider
import kotlin.reflect.KMutableProperty0

fun Cell<JSlider>.labelTable(map: Map<Int, JComponent>): Cell<JSlider> {
  component.labelTable = Hashtable(map)
  return this
}

fun Cell<JSlider>.bindValue(prop: KMutableProperty0<Int>): Cell<JSlider> {
  return bindValue(prop.toMutableProperty())
}

fun Cell<JSlider>.bindValue(getter: () -> Int, setter: (Int) -> Unit): Cell<JSlider> {
  return bindValue(MutableProperty(getter, setter))
}

fun Cell<JSlider>.showValueHint(): Cell<JSlider> {
  applyToComponent {
    toolTipText = "${value}%"
    addChangeListener { toolTipText = "${value}%" }
  }
  return this
}

private fun Cell<JSlider>.bindValue(prop: MutableProperty<Int>): Cell<JSlider> {
  return bind(JSlider::getValue, JSlider::setValue, prop)
}
