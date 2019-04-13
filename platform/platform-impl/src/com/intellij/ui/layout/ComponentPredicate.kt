// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.layout

import javax.swing.AbstractButton
import javax.swing.JComboBox

abstract class ComponentPredicate : () -> Boolean {
  abstract fun addListener(listener: (Boolean) -> Unit)
}

val AbstractButton.selected: ComponentPredicate
    get() = object : ComponentPredicate() {
      override fun invoke(): Boolean = isSelected

      override fun addListener(listener: (Boolean) -> Unit) {
        addChangeListener { listener(isSelected()) }
      }
    }

fun <T> JComboBox<T>.hasSelection(predicate: (T?) -> Boolean): ComponentPredicate {
  return object : ComponentPredicate() {
    override fun invoke(): Boolean = predicate(selectedItem as T?)

    override fun addListener(listener: (Boolean) -> Unit) {
      addActionListener { listener(predicate(selectedItem as T?)) }
    }
  }
}
