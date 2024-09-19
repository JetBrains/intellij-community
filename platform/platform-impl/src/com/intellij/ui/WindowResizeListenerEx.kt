// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Cursor
import java.awt.Insets
import javax.swing.Icon
import javax.swing.JComponent

@ApiStatus.Internal
class WindowResizeListenerEx(private val glassPane: IdeGlassPane, content: Component, border: Insets, corner: Icon?) :
  WindowResizeListener(content, border, corner) {
  private val resizeListeners = mutableListOf<Runnable>()

  private var cursor: Cursor? = null

  override fun setCursor(content: Component, cursor: Cursor) {
    if (this.cursor !== cursor || this.cursor !== Cursor.getDefaultCursor()) {
      glassPane.setCursor(cursor, this)
      this.cursor = cursor
      if (content is JComponent) {
        IdeGlassPaneImpl.savePreProcessedCursor(content, content.getCursor())
      }
    }
    // On macOS, the component's cursor may be updated asynchronously,
    // so we can't be sure it's the same as we set the last time.
    // Therefore, this call must be outside the if statement above.
    // No performance penalty for this, because there's another equality check inside.
    super.setCursor(content, cursor)
  }

  override fun notifyResized() {
    resizeListeners.forEach(Runnable::run)
  }

  fun install(parent: Disposable): WindowResizeListenerEx {
    glassPane.addMousePreprocessor(this, parent)
    glassPane.addMouseMotionPreprocessor(this, parent)
    return this
  }

  fun install(coroutineScope: CoroutineScope): WindowResizeListenerEx {
    glassPane.addMouseListener(this, coroutineScope)
    return this
  }

  fun addResizeListeners(listener: Runnable) {
    resizeListeners.add(listener)
  }

  fun removeResizeListeners(listener: Runnable) {
    resizeListeners.remove(listener)
  }
}
