// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import kotlinx.collections.immutable.toImmutableList
import java.awt.event.ActionListener
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

open class DefaultToolbarComboButtonModel : ToolbarComboButtonModel {

  protected var mySelected: Boolean = false
  protected val myActionListeners = mutableListOf<ActionListener>()
  protected val myChangeListeners = mutableListOf<ChangeListener>()

  override fun isSelected(): Boolean {
    return mySelected
  }

  override fun setSelected(b: Boolean) {
    mySelected = b
    fireChangeListeners()
  }

  override fun addActionListener(listener: ActionListener) {
    myActionListeners.add(listener)
  }

  override fun removeActionListener(listener: ActionListener) {
    myActionListeners.remove(listener)
  }

  override fun getActionListeners(): List<ActionListener> = myActionListeners.toImmutableList()

  override fun addChangeListener(l: ChangeListener) {
    myChangeListeners.add(l)
  }

  override fun removeChangeListener(l: ChangeListener) {
    myChangeListeners.remove(l)
  }

  protected fun fireChangeListeners() {
    val event = ChangeEvent(this)
    myChangeListeners.forEach { it.stateChanged(event) }
  }
}