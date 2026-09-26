// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental
package com.intellij.openapi.editor.inlay

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ComponentInlayRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Experimental
class EmbeddedEditorInlay internal constructor(
  internal val inlay: Inlay<ComponentInlayRenderer<JComponent>>,
  internal val component: JComponent,
  internal val editor: EditorEx?,
) : Disposable {
  internal val hostEditor: Editor get() = inlay.editor

  private var disposed = false

  val embeddedEditor: Editor?
    get() = editor

  init {
    Disposer.register(this, inlay)
  }

  override fun dispose() {
    if (disposed) return
    disposed = true

    val embeddedEditor = editor
    if (embeddedEditor != null && !embeddedEditor.isDisposed) {
      EditorFactory.getInstance().releaseEditor(embeddedEditor)
    }
  }
}

@ApiStatus.Experimental
@RequiresEdt
fun Editor.addEmbeddedEditorInlay(offset: Int, configure: EmbeddedEditorInlayBuilder.() -> Unit): EmbeddedEditorInlay? {
  return EmbeddedEditorInlayBuilder(this).apply(configure).build(offset)
}
