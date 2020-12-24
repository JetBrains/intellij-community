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
    val toolbarComponents = ArrayList<FloatingToolbarComponentImpl>()
    forEachProvider { provider ->
      val actionGroup = provider.actionGroup
      val autoHideable = provider.autoHideable
      val providerId = provider.id
      val component = FloatingToolbarComponentImpl(providerId, this, targetComponent, actionGroup, autoHideable, parentDisposable)
      if (provider is EditorFloatingToolbarProvider) {
        provider.register(component, parentDisposable)
      }
      toolbarComponents.add(component)
      add(component)
    }

    editor.addEditorMouseMotionListener(object : EditorMouseMotionListener {
      override fun mouseMoved(e: EditorMouseEvent) {
        if (isInsideActivationArea(container, e.mouseEvent.point)) {
          for (component in toolbarComponents) {
            if (component.autoHideable) {
              component.scheduleShow()
            }
          }
        }
      }
    })
  }

  companion object {
    private val EP_NAME = ExtensionPointName.create<FloatingToolbarProvider>("com.intellij.floatingToolbarProvider")
    private val DEPRECATED_EP_NAME = ExtensionPointName.create<EditorFloatingToolbarProvider>("com.intellij.editorFloatingToolbarProvider")

    private fun forEachProvider(action: (FloatingToolbarProvider) -> Unit) {
      EP_NAME.forEachExtensionSafe(action)
      DEPRECATED_EP_NAME.forEachExtensionSafe(action)
    }

    fun getProvider(id: String): FloatingToolbarProvider {
      return EP_NAME.findFirstSafe { it.id == id }
             ?: DEPRECATED_EP_NAME.findFirstSafe { it.id == id }!!
    }

    private fun isInsideActivationArea(container: JScrollPane, p: Point): Boolean {
      val viewport = container.viewport
      val r = viewport.bounds
      val viewPosition = viewport.viewPosition
      val activationArea = Rectangle(0, 0, r.width, r.height)
      return activationArea.contains(p.x, p.y - viewPosition.y)
    }
  }
}
