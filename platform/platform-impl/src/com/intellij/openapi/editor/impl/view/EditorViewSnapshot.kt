// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.font.FontRenderContext

internal data class EditorViewSnapshot(
  private val fontRenderContext: FontRenderContext,
  private val metrics: EditorViewMetrics,
  private val tabSize: Int,
  private val bidiFlags: Int,
  private val prefix: EditorPrefix?,
  private val paintCallback: Runnable?,
) {
  constructor(fontRenderContext: FontRenderContext) : this(
    fontRenderContext = fontRenderContext,
    bidiFlags = 0,
    tabSize = -1,
    metrics = EditorViewMetrics.UNINITIALIZED,
    prefix = null,
    paintCallback = null,
  )

  fun fontRenderContext(): FontRenderContext = fontRenderContext
  fun plainSpaceWidth(): Float = metrics.plainSpaceWidth
  fun lineHeight(): Int = metrics.lineHeight
  fun descent(): Int = metrics.descent
  fun charHeight(): Int = metrics.charHeight
  fun maxCharWidth(): Float = metrics.maxCharWidth
  fun capHeight(): Int = metrics.capHeight
  fun topOverhang(): Int = metrics.topOverhang
  fun bottomOverhang(): Int = metrics.bottomOverhang
  fun caretHeight(): Int = metrics.caretHeight
  fun caretTopOverhang(): Int = metrics.caretTopOverhang
  fun ascent(): Int = metrics.ascent
  fun prefixText(): String? = prefix?.prefixText
  fun prefixLayout(): LineLayout? = prefix?.prefixLayout
  fun prefixAttributes(): TextAttributes? = prefix?.prefixAttributes
  fun tabSize(): Int = tabSize
  fun bidiFlags(): Int = bidiFlags
  fun paintCallback(): Runnable? = paintCallback
  fun prefix(): EditorPrefix? = prefix
  fun metrics(): EditorViewMetrics = metrics

  fun withFontRenderContext(fontRenderContext: FontRenderContext): EditorViewSnapshot {
    return if (fontRenderContext === this.fontRenderContext) {
      this
    } else {
      copy(fontRenderContext = fontRenderContext)
    }
  }

  fun withMetrics(metrics: EditorViewMetrics): EditorViewSnapshot {
    return if (metrics === this.metrics) {
      this
    } else {
      copy(metrics = metrics)
    }
  }

  fun withTabSize(tabSize: Int): EditorViewSnapshot {
    return if (tabSize == this.tabSize) {
      this
    } else {
      copy(tabSize = tabSize)
    }
  }

  fun withBidiFlags(bidiFlags: Int): EditorViewSnapshot {
    return if (bidiFlags == this.bidiFlags) {
      this
    } else {
      copy(bidiFlags = bidiFlags)
    }
  }

  fun withPrefix(prefix: EditorPrefix?): EditorViewSnapshot {
    return if (prefix === this.prefix) {
      this
    } else {
      copy(prefix = prefix)
    }
  }

  fun withPrefixLayout(prefixLayout: LineLayout?): EditorViewSnapshot {
    return withPrefix(prefix?.withPrefixLayout(prefixLayout))
  }

  fun withPaintCallback(paintCallback: Runnable?): EditorViewSnapshot {
    return if (paintCallback === this.paintCallback) {
      this
    } else {
      copy(paintCallback = paintCallback)
    }
  }
}
