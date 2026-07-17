// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.inlay

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ComponentInlayRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
class EditorInlay internal constructor(
  val inlay: Inlay<ComponentInlayRenderer<JComponent>>,
  val component: JComponent,
  val embeddedEditor: EditorEx?,
) : Disposable {
  val hostEditor: Editor get() = inlay.editor

  private var disposed = false

  init {
    Disposer.register(this, inlay)
  }

  override fun dispose() {
    if (disposed) return
    disposed = true

    val embeddedEditor = embeddedEditor
    if (embeddedEditor != null && !embeddedEditor.isDisposed) {
      EditorFactory.getInstance().releaseEditor(embeddedEditor)
    }
  }
}

@ApiStatus.Internal
@RequiresEdt
fun Editor.addEditorInlay(offset: Int, configure: EditorInlayBuilder.() -> Unit): EditorInlay? {
  ThreadingAssertions.assertEventDispatchThread()
  return EditorInlayBuilder(this).apply(configure).build(offset)
}
