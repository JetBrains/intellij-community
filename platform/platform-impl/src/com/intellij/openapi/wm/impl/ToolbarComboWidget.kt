// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.event.ActionListener
import java.awt.event.InputEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.UIManager
import kotlin.properties.Delegates
import kotlin.reflect.KProperty

@Suppress("LeakingThis")
abstract class ToolbarComboWidget: JComponent() {

  val pressListeners: MutableList<ActionListener> = mutableListOf<ActionListener>()

  var text: @Nls String? by Delegates.observable("", this::fireUpdateEvents)

  var leftIcons: List<Icon> by Delegates.observable(emptyList(), this::fireUpdateEvents)
  var rightIcons: List<Icon> by Delegates.observable(emptyList(), this::fireUpdateEvents)
  var leftIconsGap: Int by Delegates.observable(0, this::fireUpdateEvents)
  var rightIconsGap: Int by Delegates.observable(0, this::fireUpdateEvents)
  var hoverBackground: Color? by Delegates.observable(null, this::fireUpdateEvents)
  var transparentHoverBackground: Color? by Delegates.observable(null, this::fireUpdateEvents)
  var isExpandable: Boolean by Delegates.observable(true, this::fireUpdateEvents)

  init {
    updateUI() //set UI for component
    isOpaque = false
  }

  abstract fun doExpand(e: InputEvent?)

  override fun getUIClassID(): String {
    return "ToolbarComboWidgetUI"
  }

  override fun updateUI() {
    setUI(UIManager.getUI(this))
  }

  fun addPressListener(action: ActionListener) {
    val listenersCountBefore = pressListeners.size
    pressListeners += action
    firePropertyChange("pressListenersCount", listenersCountBefore, pressListeners.size)
  }

  private fun fireUpdateEvents(prop: KProperty<*>, oldValue: Any?, newValue: Any?) {
    firePropertyChange(prop.name, oldValue, newValue)
    invalidate()
    repaint()
  }
}
