// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Font
import java.awt.Rectangle
import javax.swing.Scrollable
import javax.swing.SwingConstants

/**
 * Computes the unit/block scroll increments that [EditorComponentImpl] reports through the Swing
 * [Scrollable] contract (and thus what the platform's wheel/scrollbar scrolling snaps to).
 *
 * Use [EditorImpl.setScrollableIncrementProvider] to install a custom provider.
 */
@ApiStatus.Internal
interface EditorScrollableIncrementProvider {
  /**
   * @param visibleRect the view area visible within the viewport
   * @param orientation [SwingConstants.VERTICAL] or [SwingConstants.HORIZONTAL]
   * @param direction less than zero to scroll up/left, greater than zero for down/right.
   */
  fun getScrollableUnitIncrement(
    editor: Editor,
    visibleRect: Rectangle,
    orientation: Int,
    direction: Int,
  ): Int

  /**
   * @param visibleRect the view area visible within the viewport
   * @param orientation [SwingConstants.VERTICAL] or [SwingConstants.HORIZONTAL]
   * @param direction less than zero to scroll up/left, greater than zero for down/right.
   */
  fun getScrollableBlockIncrement(
    editor: Editor,
    visibleRect: Rectangle,
    orientation: Int,
    direction: Int,
  ): Int

  companion object {
    @JvmField
    val DEFAULT: EditorScrollableIncrementProvider = DefaultEditorScrollableIncrementProvider()
  }
}

private class DefaultEditorScrollableIncrementProvider : EditorScrollableIncrementProvider {
  override fun getScrollableUnitIncrement(editor: Editor, visibleRect: Rectangle, orientation: Int, direction: Int): Int {
    return if (orientation == SwingConstants.VERTICAL) {
      editor.lineHeight
    }
    else {
      // orientation == SwingConstants.HORIZONTAL
      EditorUtil.getSpaceWidth(Font.PLAIN, editor)
    }
  }

  override fun getScrollableBlockIncrement(editor: Editor, visibleRect: Rectangle, orientation: Int, direction: Int): Int {
    if (orientation != SwingConstants.VERTICAL) {
      // orientation == SwingConstants.HORIZONTAL
      return visibleRect.width
    }
    val lineHeight = editor.lineHeight
    return if (direction > 0) {
      val lineNumber = (visibleRect.y + visibleRect.height) / lineHeight
      lineHeight * lineNumber - visibleRect.y
    }
    else {
      val lineNumber = (visibleRect.y - visibleRect.height) / lineHeight
      visibleRect.y - lineHeight * lineNumber
    }
  }
}