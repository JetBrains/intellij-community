// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.observable.util.whenKeyPressed
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

@ApiStatus.Internal
class FloatingToolbarComponentImpl(
  editor: EditorEx,
  provider: FloatingToolbarProvider,
  parentDisposable: Disposable
) : AbstractFloatingToolbarComponent(provider.actionGroup, provider.autoHideable) {

  init {
    init(editor.contentComponent)

    if (provider.autoHideable) {
      initAutoHideableListeners(editor)
    }

    provider.register(editor.dataContext, this, this)
    Disposer.register(parentDisposable, this)
  }

  private fun initAutoHideableListeners(editor: EditorEx) {
    var ignoreMouseMotionRectangle: Rectangle? = null
    editor.addEditorMouseMotionListener(object : EditorMouseMotionListener {
      override fun mouseMoved(e: EditorMouseEvent) {
        ignoreMouseMotionRectangle?.let {
          if (!it.contains(e.mouseEvent.locationOnScreen)) {
            ignoreMouseMotionRectangle = null
          }
        }
        if (ignoreMouseMotionRectangle == null) {
          scheduleShow()
        }
      }
    }, this)
    editor.contentComponent.whenKeyPressed(this) {
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