// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.observable.util.whenKeyPressed
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle
import java.awt.event.KeyEvent

@ApiStatus.Internal
class FloatingToolbarComponentImpl(
  editor: EditorEx,
  provider: FloatingToolbarProvider,
  parentDisposable: Disposable
) : AbstractFloatingToolbarComponent(provider.actionGroup, provider.autoHideable) {

  private var ignoreMouseMotionRectangle: Rectangle? = null

  init {
    init(editor.contentComponent)
    editor.addEditorMouseMotionListener(object : EditorMouseMotionListener {
      override fun mouseMoved(e: EditorMouseEvent) {
        if (provider.autoHideable) {
          ignoreMouseMotionRectangle?.let {
            if (!it.contains(e.mouseEvent.locationOnScreen)) {
              ignoreMouseMotionRectangle = null
            }
          }
          if (ignoreMouseMotionRectangle == null) {
            scheduleShow()
          }
        }
      }
    })
    editor.contentComponent.whenKeyPressed(parentDisposable) {
      if (it.keyCode == KeyEvent.VK_ESCAPE) {
        if (isVisible) {
          val visibleRectOnScreen = visibleRect
          if (visibleRectOnScreen != null) {
            visibleRectOnScreen.location = locationOnScreen
            ignoreMouseMotionRectangle = visibleRectOnScreen
          }
        }
        hideImmediately()
      }
    }

    provider.register(editor.dataContext, this, this)
    Disposer.register(parentDisposable, this)
  }
}