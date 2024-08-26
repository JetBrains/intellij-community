// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import com.intellij.util.ui.MacUIUtil
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
    // macOS sometimes ignores [NSCursor set] for no reason.
    // Force it by calling the native method every time.
    // It may fail once, but it'll work on the next try, or the next one, or the next one...
    // It works, but with a performance penalty, so we're only doing it for resize cursors,
    // which are important to get right, otherwise the user has no visual clue that resizing
    // is even possible (IJPL-43686).
    if (SystemInfoRt.isMac && cursor.isResizeCursor()) {
      MacUIUtil.nativeSetBuiltInCursor(cursor.type)
    }
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

private fun Cursor.isResizeCursor(): Boolean = type in RESIZE_CURSOR_TYPES

private val RESIZE_CURSOR_TYPES: Set<Int> = setOf(
  Cursor.NW_RESIZE_CURSOR,
  Cursor.SW_RESIZE_CURSOR,
  Cursor.NE_RESIZE_CURSOR,
  Cursor.SE_RESIZE_CURSOR,
  Cursor.N_RESIZE_CURSOR,
  Cursor.S_RESIZE_CURSOR,
  Cursor.W_RESIZE_CURSOR,
  Cursor.E_RESIZE_CURSOR,
)
