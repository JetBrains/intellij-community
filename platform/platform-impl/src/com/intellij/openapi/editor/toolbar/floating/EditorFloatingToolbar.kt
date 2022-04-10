// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.impl.EditorImpl
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
    val toolbarComponents = ArrayList<FloatingToolbarComponentImpl>()
    FloatingToolbarProvider.EP_NAME.forEachExtensionSafe { provider ->
      val actionGroup = provider.actionGroup
      val autoHideable = provider.autoHideable
      val component = FloatingToolbarComponentImpl(this, targetComponent, actionGroup, autoHideable, parentDisposable)
      provider.register(editor.dataContext, component, parentDisposable)
      toolbarComponents.add(component)
      add(component)
    }

    val executor = ExecutorWithThrottling(SCHEDULE_SHOW_DELAY)
    editor.addEditorMouseMotionListener(object : EditorMouseMotionListener {
      override fun mouseMoved(e: EditorMouseEvent) {
        executor.executeOrSkip {
          if (isInsideActivationArea(container, e.mouseEvent.point)) {
            for (component in toolbarComponents) {
              if (component.autoHideable) {
                component.scheduleShow()
              }
            }
          }
        }
      }
    })
  }

  companion object {
    // Should be less than [VisibilityController.RETENTION_DELAY]
    private const val SCHEDULE_SHOW_DELAY = 1000

    private fun isInsideActivationArea(container: JScrollPane, p: Point): Boolean {
      val viewport = container.viewport
      val r = viewport.bounds
      val viewPosition = viewport.viewPosition
      val activationArea = Rectangle(0, 0, r.width, r.height)
      return activationArea.contains(p.x, p.y - viewPosition.y)
    }
  }
}
