// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.observable.util.addComponent
import com.intellij.openapi.observable.util.whenKeyPressed
import com.intellij.openapi.ui.isComponentUnderMouse
import com.intellij.openapi.ui.isFocusAncestor
import java.awt.FlowLayout
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.SwingUtilities

class EditorFloatingToolbar(editor: EditorImpl) : JPanel() {
  init {
    layout = FlowLayout(FlowLayout.RIGHT, 20, 20)
    border = BorderFactory.createEmptyBorder()
    isOpaque = false

    FloatingToolbarProvider.EP_NAME.forEachExtensionSafe { provider ->
      addFloatingToolbarComponent(editor, provider)
    }
    FloatingToolbarProvider.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<FloatingToolbarProvider> {
      override fun extensionAdded(extension: FloatingToolbarProvider, pluginDescriptor: PluginDescriptor) {
        addFloatingToolbarComponent(editor, extension)
      }
    }, editor.disposable)
  }

  private fun addFloatingToolbarComponent(editor: EditorImpl, provider: FloatingToolbarProvider) {
    if (provider.isApplicable(editor.dataContext)) {
      val disposable = FloatingToolbarProvider.createExtensionDisposable(provider, editor.disposable)
      val component = EditorFloatingToolbarComponent(editor, provider, disposable)
      addComponent(component, disposable)
    }
  }

  private class EditorFloatingToolbarComponent(
    private val editor: EditorImpl,
    private val provider: FloatingToolbarProvider,
    private val parentDisposable: Disposable
  ) : AbstractFloatingToolbarComponent(provider.actionGroup, parentDisposable) {

    override val autoHideable: Boolean = provider.autoHideable

    override fun isComponentOnHold(): Boolean {
      return component.parent?.isComponentUnderMouse() == true ||
             component.parent?.isFocusAncestor() == true
    }

    override fun installMouseMotionWatcher() {
      if (provider.autoHideable) {
        var ignoreMouseMotionRectangle: Rectangle? = null
        editor.addEditorMouseMotionListener(object : EditorMouseMotionListener {
          override fun mouseMoved(e: EditorMouseEvent) {
            if (ignoreMouseMotionRectangle?.contains(e.mouseEvent.locationOnScreen) != true) {
              ignoreMouseMotionRectangle = null
            }
            if (ignoreMouseMotionRectangle == null) {
              scheduleShow()
            }
          }
        }, parentDisposable)
        editor.contentComponent.whenKeyPressed(parentDisposable) {
          if (it.keyCode == KeyEvent.VK_ESCAPE) {
            if (isVisible) {
              val location = Point()
              SwingUtilities.convertPointToScreen(location, this)
              ignoreMouseMotionRectangle = Rectangle(location, size)
            }
            hideImmediately()
          }
        }
      }
    }

    init {
      init(editor.contentComponent)
      provider.register(editor.dataContext, this, parentDisposable)
    }
  }
}
