// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.ui.layout.*
import java.util.*
import javax.swing.JComponent
import javax.swing.JSlider

fun Cell<JSlider>.labelTable(map: Map<Int, JComponent>): Cell<JSlider> {
  component.labelTable = Hashtable(map)
  return this
}

fun Cell<JSlider>.bindValue(modelBinding: PropertyBinding<Int>): Cell<JSlider> {
  return bind(JSlider::getValue, JSlider::setValue, modelBinding)
}

fun Cell<JSlider>.showValueHint(): Cell<JSlider> {
  applyToComponent {
    toolTipText = "${value}%"
    addChangeListener { toolTipText = "${value}%" }
  }
  return this
}
