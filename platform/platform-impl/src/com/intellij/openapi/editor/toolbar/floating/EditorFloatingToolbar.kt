// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.createExtensionDisposable
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.withEachExtensionSafe
import com.intellij.openapi.observable.util.addComponent
import com.intellij.openapi.observable.util.whenKeyPressed
import com.intellij.openapi.ui.isComponentUnderMouse
import com.intellij.openapi.ui.isFocusAncestor
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.FlowLayout
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@ApiStatus.Internal
class EditorFloatingToolbar(editor: EditorImpl) :
  JPanel(),
  Disposable.Default {

  init {
    layout = FlowLayout(FlowLayout.RIGHT, 20, 20)
    border = BorderFactory.createEmptyBorder()
    isOpaque = false

    FloatingToolbarProvider.EP_NAME.launchEachExtensionSafe(
      toolbarDisposable = this,
      context = Dispatchers.EDT,
    ) { providerDisposable, provider ->
      val dataContext = editor.dataContext
      val providerApplicable = provider.isApplicableAsync(dataContext)
      if (providerApplicable) {
        val component = EditorFloatingToolbarComponent(editor, provider, providerDisposable)
        addComponent(component, providerDisposable)
        provider.register(dataContext, component, providerDisposable)
      }
    }
  }

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

  companion object {

    @OptIn(DelicateCoroutinesApi::class)
    private fun <Extension : Any> ExtensionPointName<Extension>.launchEachExtensionSafe(
      toolbarDisposable: Disposable,
      context: CoroutineContext = EmptyCoroutineContext,
      handler: suspend CoroutineScope.(Disposable, Extension) -> Unit,
    ) {
      @Suppress("DEPRECATION")
      withEachExtensionSafe(toolbarDisposable) { extension ->
        val extensionDisposable = createExtensionDisposable(extension, toolbarDisposable)
        GlobalScope.childScope("EditorFloatingToolbar").launch(context) {
          runCatching { handler(extensionDisposable, extension) }
            .onFailure { Disposer.dispose(extensionDisposable) }
            .getOrLogException(logger<ExtensionPointImpl<*>>())
        }.cancelOnDispose(extensionDisposable)
      }
    }
  }
}
