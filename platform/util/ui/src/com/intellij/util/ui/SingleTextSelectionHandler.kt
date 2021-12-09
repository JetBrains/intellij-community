// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui

import javax.swing.event.CaretEvent
import javax.swing.event.CaretListener
import javax.swing.text.JTextComponent

/**
 * @author Alexander Lobas
 */
class SingleTextSelectionHandler : CaretListener {
  private val myComponents = ArrayList<JTextComponent>()
  private var myIgnoreEvents = false

  fun add(component: JTextComponent, start: Boolean) {
    myComponents.add(component)
    if (start) {
      component.addCaretListener(this)
    }
  }

  fun remove(component: JTextComponent) {
    myComponents.remove(component)
    component.removeCaretListener(this)
  }

  fun start() {
    if (myComponents.size > 1) {
      for (component in myComponents) {
        component.addCaretListener(this)
      }
    }
  }

  override fun caretUpdate(e: CaretEvent) {
    if (!myIgnoreEvents) {
      myIgnoreEvents = true
      try {
        val source = e.source
        for (component in myComponents) {
          if (component !== source && component.selectionStart != component.selectionEnd) {
            component.select(0, 0)
          }
        }
      }
      finally {
        myIgnoreEvents = false
      }
    }
  }
}