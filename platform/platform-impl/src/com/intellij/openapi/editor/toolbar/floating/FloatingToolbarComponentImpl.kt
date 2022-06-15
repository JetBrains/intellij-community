// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class FloatingToolbarComponentImpl(
  editor: EditorEx,
  provider: FloatingToolbarProvider,
  parentDisposable: Disposable
) : AbstractFloatingToolbarComponent(provider.actionGroup, provider.autoHideable) {

  init {
    init(editor.contentComponent)
    editor.addEditorMouseMotionListener(object : EditorMouseMotionListener {
      override fun mouseMoved(e: EditorMouseEvent) {
        if (provider.autoHideable) {
          scheduleShow()
        }
      }
    })

    provider.register(editor.dataContext, this, this)
    Disposer.register(parentDisposable, this)
  }
}