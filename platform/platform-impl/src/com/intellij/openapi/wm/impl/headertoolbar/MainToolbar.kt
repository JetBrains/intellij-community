// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbarWidgetFactory.Position
import com.intellij.ui.components.panels.HorizontalLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.UIManager

class MainToolbar: JPanel(HorizontalLayout(10)) {

  private val layoutMap = mapOf(
    Position.Left to HorizontalLayout.LEFT,
    Position.Right to HorizontalLayout.RIGHT,
    Position.Center to HorizontalLayout.CENTER
  )
  private val visibleComponentsPool = VisibleComponentsPool()

  init {
    background = UIManager.getColor("MainToolbar.background")
    isOpaque = true
    for (factory in MainToolbarWidgetFactory.EP_NAME.extensionList) {
      addWidget(factory.createWidget(), factory.getPosition())
    }

    createActionsBar()?.let { addWidget(it, Position.Right) }
    addComponentListener(ResizeListener())
  }

  private fun addWidget(widget: JComponent,
                        position: Position) {
    add(layoutMap[position], widget)
    visibleComponentsPool.addElement(widget, position)
  }

  private fun createActionsBar(): JComponent? {
    val group = CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_EXPERIMENTAL_TOOLBAR_ACTIONS) as ActionGroup?
    return group?.let { ActionToolbar(it.getChildren(null).asList()) }
  }

  private inner class ResizeListener : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent?) {
      val visibleElementsWidth = components.filter { it.isVisible }.sumOf { it.preferredSize.width }
      val componentWidth = size.width
      if (visibleElementsWidth > componentWidth) {
        decreaseVisibleSizeBy(visibleElementsWidth - componentWidth)
      }
      else {
        increaseVisibleSizeBy(componentWidth - visibleElementsWidth)
      }
    }

    private fun increaseVisibleSizeBy(delta: Int) {
      var restDelta = delta
      var comp = visibleComponentsPool.nextToShow()
      while (comp != null && restDelta > 0) {
        val width = comp.preferredSize.width
        if (width > restDelta) return
        comp.isVisible = true
        restDelta -= width
        comp = visibleComponentsPool.nextToShow()
      }
    }

    private fun decreaseVisibleSizeBy(delta: Int) {
      var restDelta = delta
      var comp = visibleComponentsPool.nextToHide()
      while (comp != null && restDelta > 0) {
        comp.isVisible = false
        restDelta -= comp.preferredSize.width
        comp = visibleComponentsPool.nextToShow()
      }
    }
  }
}

private class VisibleComponentsPool {

  val elements = mapOf<Position, MutableList<JComponent>>(
    Pair(Position.Left, mutableListOf()),
    Pair(Position.Right, mutableListOf()),
    Pair(Position.Center, mutableListOf())
  )

  fun addElement(comp: JComponent, position: Position) = elements[position]!!.add(comp)

  fun nextToShow(): JComponent? {
    return elements[Position.Center]!!.firstOrNull { !it.isVisible }
           ?: elements[Position.Right]!!.firstOrNull { !it.isVisible }
           ?: elements[Position.Left]!!.firstOrNull { !it.isVisible }
  }

  fun nextToHide(): JComponent? {
    return elements[Position.Left]!!.lastOrNull() { it.isVisible }
           ?: elements[Position.Right]!!.lastOrNull() { it.isVisible }
           ?: elements[Position.Center]!!.lastOrNull() { it.isVisible }
  }
}

internal fun isToolbarInHeader(settings : UISettings) : Boolean {
  return SystemInfo.isWindows && !settings.separateMainMenu
}