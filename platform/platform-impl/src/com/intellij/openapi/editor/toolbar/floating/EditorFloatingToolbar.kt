// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.createExtensionDisposable
import com.intellij.openapi.observable.util.addComponent
import com.intellij.openapi.observable.util.whenKeyPressed
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.ui.isComponentUnderMouse
import com.intellij.openapi.ui.isFocusAncestor
import com.intellij.openapi.util.Disposer
import com.jetbrains.rd.util.threading.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.awt.FlowLayout
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.SwingUtilities

class EditorFloatingToolbar(editor: EditorImpl) :
  JPanel(),
  Disposable {

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
    }, this)

    Disposer.register(editor.disposable, this)
  }

  private fun addFloatingToolbarComponent(
    editor: EditorImpl,
    provider: FloatingToolbarProvider,
  ) {
    createLifetime().launch {
      val dataContext = withContext(Dispatchers.EDT) {
        editor.dataContext
      }
      val providerApplicable = readAction {
        provider.isApplicable(dataContext)
      }
      if (providerApplicable) {
        withContext(Dispatchers.EDT) {
          coroutineContext.ensureActive()

          val extensionDisposable = try {
            FloatingToolbarProvider.EP_NAME.createExtensionDisposable(
              extension = provider,
              parentDisposable = this@EditorFloatingToolbar,
            )
          }
          catch (_: Exception) {
            null
          }

          if (extensionDisposable == null)
            return@withContext

          val component = EditorFloatingToolbarComponent(
            editor = editor,
            provider = provider,
            parentDisposable = extensionDisposable,
          )

          addComponent(component, extensionDisposable)
          provider.register(dataContext, component, extensionDisposable)
        }
      }
    }
  }

  override fun dispose() {}

  private class EditorFloatingToolbarComponent(
    editor: EditorImpl,
    provider: FloatingToolbarProvider,
    parentDisposable: Disposable,
  ) : AbstractFloatingToolbarComponent(
    provider.actionGroup,
    editor.contentComponent,
    parentDisposable
  ) {

    override fun isComponentOnHold(): Boolean {
      return component.parent?.isComponentUnderMouse() == true ||
             component.parent?.isFocusAncestor() == true
    }

    init {
      backgroundAlpha = provider.backgroundAlpha
      showingTime = provider.showingTime
      hidingTime = provider.hidingTime
      retentionTime = provider.retentionTime
      autoHideable = provider.autoHideable
    }

    init {
      var ignoreMouseMotionRectangle: Rectangle? = null
      editor.addEditorMouseMotionListener(object : EditorMouseMotionListener {
        override fun mouseMoved(e: EditorMouseEvent) {
          if (ignoreMouseMotionRectangle?.contains(e.mouseEvent.locationOnScreen) != true) {
            ignoreMouseMotionRectangle = null
          }
          if (autoHideable && ignoreMouseMotionRectangle == null) {
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
          provider.onHiddenByEsc(editor.dataContext)
        }
      }
    }
  }
}
