// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.util.Disposer
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JPanel

class EditorFloatingToolbar(editor: EditorImpl) : JPanel() {
  init {
    layout = FlowLayout(FlowLayout.RIGHT, 20, 20)
    border = BorderFactory.createEmptyBorder()
    isOpaque = false

    FloatingToolbarProvider.EP_NAME.forEachExtensionSafe { provider ->
      addFloatingToolbarComponent(provider, editor)
    }
    FloatingToolbarProvider.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<FloatingToolbarProvider> {
      override fun extensionAdded(extension: FloatingToolbarProvider, pluginDescriptor: PluginDescriptor) {
        addFloatingToolbarComponent(extension, editor)
      }
    }, editor.disposable)

    val executor = ExecutorWithThrottling(SCHEDULE_SHOW_DELAY)
    editor.addEditorMouseMotionListener(object : EditorMouseMotionListener {
      override fun mouseMoved(e: EditorMouseEvent) {
        executor.executeOrSkip {
          components.asSequence()
            .filterIsInstance<FloatingToolbarComponentImpl>()
            .filter { it.autoHideable }
            .forEach { it.scheduleShow() }
        }
      }
    })
  }

  private fun addFloatingToolbarComponent(provider: FloatingToolbarProvider, editor: EditorImpl) {
    val disposable = createExtensionDisposable(provider, editor.disposable)
    val targetComponent = editor.contentComponent
    val actionGroup = provider.actionGroup
    val autoHideable = provider.autoHideable
    val component = FloatingToolbarComponentImpl(this, targetComponent, actionGroup, autoHideable, disposable)
    provider.register(editor.dataContext, component, disposable)
    add(component, disposable)
  }

  private fun add(component: Component, parentDisposable: Disposable) {
    add(component)
    parentDisposable.whenDisposed {
      remove(component)
    }
  }

  private fun createExtensionDisposable(provider: FloatingToolbarProvider, parentDisposable: Disposable): Disposable {
    val disposable = Disposer.newDisposable()
    Disposer.register(parentDisposable, disposable)
    FloatingToolbarProvider.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<FloatingToolbarProvider> {
      override fun extensionRemoved(extension: FloatingToolbarProvider, pluginDescriptor: PluginDescriptor) {
        if (provider === extension) {
          Disposer.dispose(disposable)
        }
      }
    }, disposable)
    return disposable
  }

  companion object {
    // Should be less than [VisibilityController.RETENTION_DELAY]
    private const val SCHEDULE_SHOW_DELAY = 1000
  }
}
