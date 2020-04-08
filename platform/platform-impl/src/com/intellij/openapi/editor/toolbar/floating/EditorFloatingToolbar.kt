// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.extensions.ExtensionPointName
import java.awt.FlowLayout
import java.awt.Point
import java.awt.Rectangle
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JScrollPane

class EditorFloatingToolbar(editor: EditorImpl) : JPanel() {
  init {
    layout = FlowLayout(FlowLayout.RIGHT, 20, 20)
    border = BorderFactory.createEmptyBorder()
    isOpaque = false

    val targetComponent = editor.contentComponent
    val container = editor.scrollPane
    val parentDisposable = editor.disposable
    val toolbarComponents = ArrayList<Pair<FloatingToolbarProvider, FloatingToolbarComponentImpl>>()
    EP_NAME.forEachExtensionSafe { provider ->
      val actionGroup = provider.actionGroup
      val autoHideable = provider.autoHideable
      val component = FloatingToolbarComponentImpl(this, targetComponent, actionGroup, autoHideable, parentDisposable)
      provider.register(component, parentDisposable)
      toolbarComponents.add(provider to component)
    }
    toolbarComponents.sortBy { it.first.priority }
    toolbarComponents.forEach { add(it.second) }

    editor.addEditorMouseMotionListener(object : EditorMouseMotionListener {
      var lastUpdateTime = Long.MIN_VALUE

      override fun mouseMoved(e: EditorMouseEvent) {
        if (!isInsideActivationArea(container, e.mouseEvent.point)) return
        for ((provider, component) in toolbarComponents) {
          if (!provider.autoHideable) continue

          val currentTime = System.nanoTime()
          if (currentTime > lastUpdateTime + ACTION_UPDATE_THROTTLE_DELAY_NS) {
            component.update()
            lastUpdateTime = currentTime
          }
          component.scheduleShow()
        }
      }
    })
  }

  companion object {
    val EP_NAME = ExtensionPointName.create<FloatingToolbarProvider>("com.intellij.editorFloatingToolbarProvider")
    const val ACTION_UPDATE_THROTTLE_DELAY_NS = 500_000_000

    private fun isInsideActivationArea(container: JScrollPane, p: Point): Boolean {
      val viewport = container.viewport
      val r = viewport.bounds
      val viewPosition = viewport.viewPosition
      val activationArea = Rectangle(0, 0, r.width, r.height)
      return activationArea.contains(p.x, p.y - viewPosition.y)
    }
  }
}
