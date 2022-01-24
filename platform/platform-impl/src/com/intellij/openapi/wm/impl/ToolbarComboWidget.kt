// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.ui.JBColor
import com.intellij.ui.hover.HoverStateListener
import java.awt.Color
import java.awt.Component
import java.awt.event.ActionListener
import java.awt.event.InputEvent
import javax.swing.Icon
import javax.swing.JComponent
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
    updateUI() //set UI for component

    foreground = JBColor.namedColor("MainToolbar.Dropdown.foreground", JBColor.foreground())
    background = JBColor.namedColor("MainToolbar.Dropdown.background", JBColor.background())
    hoverBackground = JBColor.namedColor("MainToolbar.Dropdown.hoverBackground", JBColor.background())

    val hoverListener = object : HoverStateListener() {
      override fun hoverChanged(component: Component, hovered: Boolean) {
        (component as JComponent).isOpaque = hovered
      }
    }
    isOpaque = false
    hoverListener.addTo(this)
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
