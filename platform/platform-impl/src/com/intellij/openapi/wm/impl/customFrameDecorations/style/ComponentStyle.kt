// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.style

import org.jetbrains.annotations.NonNls
import java.awt.MouseInfo
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.SwingUtilities

class ComponentStyle<T : JComponent>private constructor(private val default: Properties, private val styleMap: Map<ComponentStyleState, Properties>) {
  companion object {
    @NonNls const val ENABLED_PROPERTY = "enabled"
  }

  internal fun applyStyle(component: T) {
    val base = StyleProperty.getPropertiesSnapshot(component)

    val componentState = ComponentState(base).apply {
      hovered = isMouseOver(component)
      pressed = false
    }

    val styleListener = StyleComponentListener(component, this, componentState)
    component.addPropertyChangeListener(ENABLED_PROPERTY, styleListener)

    checkState(component, componentState, styleListener)
  }

  private fun isMouseOver(component: T): Boolean {
    val location = MouseInfo.getPointerInfo()?.location
    if (location != null) {
      SwingUtilities.convertPointFromScreen(location, component)
      return component.contains(location)
    }
    return false
  }

  fun updateStyle(component: T, componentState: ComponentState) {
    val properties = componentState.base.clone()
    properties.updateBy(default)

    if (!component.isEnabled) {
      if (ComponentStyleState.DISABLED in styleMap) properties.updateBy(styleMap[ComponentStyleState.DISABLED]!!)
      properties.applyTo(component)
      return
    }
    if (componentState.hovered && ComponentStyleState.HOVERED in styleMap) properties.updateBy(styleMap[ComponentStyleState.HOVERED]!!)
    if (componentState.pressed && ComponentStyleState.PRESSED in styleMap) properties.updateBy(styleMap[ComponentStyleState.PRESSED]!!)

    properties.applyTo(component)
  }

  private fun checkState(component: T, componentState: ComponentState, mouseListener: MouseListener) {
    if (component.isEnabled) {
      if (ComponentStyleState.HOVERED in styleMap || ComponentStyleState.PRESSED in styleMap) {
        componentState.hovered = isMouseOver(component)
        componentState.pressed = false

        if (mouseListener !in component.mouseListeners)
          component.addMouseListener(mouseListener)
      }
    }
    else {
      component.removeMouseListener(mouseListener)
    }
    updateStyle(component, componentState)
  }

  class ComponentStyleBuilder<T : JComponent>(val default: Properties) {
    constructor(init: Properties.() -> Unit) : this(Properties().apply { init() })

    private val styleMap: HashMap<ComponentStyleState, Properties> = HashMap()

    fun style(state: ComponentStyleState, init: Properties.() -> Unit): ComponentStyleBuilder<T> {
      val prop = Properties()
      prop.init()
      styleMap[state] = prop
      return this
    }

    fun updateDefault(init: Properties.() -> Unit): ComponentStyleBuilder<T> {
      val prop = Properties()
      prop.init()
      default.updateBy(prop)
      return this
    }

    fun updateState(state: ComponentStyleState, init: Properties.() -> Unit): ComponentStyleBuilder<T> {
      val prop = Properties()
      prop.init()
      styleMap[state]?.updateBy(prop)
      if (styleMap[state] == null) {
        styleMap[state] = prop
      }
      return this
    }

    fun clone(): ComponentStyleBuilder<T> {
      val csb = ComponentStyleBuilder<T>(default.clone())
      for ((k, v) in styleMap) {
        csb.styleMap[k] = v.clone()
      }
      return csb
    }

    fun build(): ComponentStyle<T> {
      return ComponentStyle<T>(default.clone(), styleMap.mapValues { it.value.clone()})
    }
  }

  class ComponentState(val base: Properties) {
    var hovered = false
    var pressed = false
  }

  class StyleComponentListener<T : JComponent>(val component: T,
                                               val style: ComponentStyle<T>,
                                               private val componentState: ComponentState) : PropertyChangeListener, MouseAdapter() {
    override fun mouseReleased(e: MouseEvent) {
      componentState.pressed = false
      updateStyle()
    }

    override fun mouseEntered(e: MouseEvent) {
      componentState.hovered = true
      updateStyle()
    }

    override fun mouseExited(e: MouseEvent) {
      componentState.hovered = false
      updateStyle()
    }

    override fun mousePressed(e: MouseEvent) {
      componentState.pressed = true
      updateStyle()
    }

    private fun updateStyle() {
      style.updateStyle(component, componentState)
    }

    override fun propertyChange(evt: PropertyChangeEvent?) {
      style.checkState(component, componentState, this)
    }

    fun destroy() {
      component.removePropertyChangeListener(this)
      component.removeMouseListener(this)

      componentState.base.applyTo(component)
    }
  }
}

enum class ComponentStyleState {
  HOVERED, PRESSED, DISABLED
}

