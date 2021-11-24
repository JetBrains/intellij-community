// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import java.awt.Color
import java.awt.event.ActionListener
import java.awt.event.InputEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

abstract class ToolbarComboWidget: JComponent() {

  val pressListeners = mutableListOf<ActionListener>()

  var text: String? by Delegates.observable("", this::fireUpdateEvents)
  var leftIcons: List<Icon> by Delegates.observable(emptyList(), this::fireUpdateEvents)
  var rightIcons: List<Icon> by Delegates.observable(emptyList(), this::fireUpdateEvents)
  var hoverBackground: Color by Delegates.observable(UIManager.getColor("ToolbarComboWidget.hoverBackground"), this::fireUpdateEvents)

  init {
    updateUI()
  }

  abstract fun doExpand(e: InputEvent)

  override fun getUIClassID(): String {
    return "ToolbarComboWidgetUI"
  }

  override fun updateUI() {
    setUI(UIManager.getUI(this))
  }

  fun addPressListener(action: ActionListener) {
    pressListeners += action
  }

  private fun fireUpdateEvents(prop: KProperty<*>, oldValue: Any?, newValue: Any?) {
    firePropertyChange(prop.name, oldValue, newValue)
    invalidate()
    repaint()
  }
}
