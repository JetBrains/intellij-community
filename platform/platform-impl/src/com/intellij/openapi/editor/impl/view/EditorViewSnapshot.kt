// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.font.FontRenderContext

internal data class EditorViewSnapshot(
  @JvmField val fontRenderContext: FontRenderContext,
  @JvmField val prefixText: String?,
  @JvmField val prefixLayout: LineLayout?,
  @JvmField val prefixAttributes: TextAttributes?,
  @JvmField val bidiFlags: Int,
  @JvmField val tabSize: Int,
  @JvmField val paintCallback: Runnable?,
  @JvmField val metrics: EditorViewMetrics?,
) {
  constructor(fontRenderContext: FontRenderContext) : this(
    fontRenderContext = fontRenderContext,
    prefixText = null,
    prefixLayout = null,
    prefixAttributes = null,
    bidiFlags = 0,
    tabSize = -1,
    paintCallback = null,
    metrics = null,
  )

  fun withFontRenderContext(fontRenderContext: FontRenderContext): EditorViewSnapshot {
    return if (fontRenderContext === this.fontRenderContext) {
      this
    } else {
      copy(fontRenderContext = fontRenderContext)
    }
  }

  fun withPrefixText(prefixText: String?): EditorViewSnapshot {
    return if (prefixText == this.prefixText) {
      this
    } else {
      copy(prefixText = prefixText)
    }
  }

  fun withPrefixLayout(prefixLayout: LineLayout?): EditorViewSnapshot {
    return if (prefixLayout === this.prefixLayout) {
      this
    } else {
      copy(prefixLayout = prefixLayout)
    }
  }

  fun withPrefixAttributes(prefixAttributes: TextAttributes?): EditorViewSnapshot {
    return if (prefixAttributes === this.prefixAttributes) {
      this
    } else {
      copy(prefixAttributes = prefixAttributes)
    }
  }

  fun withBidiFlags(bidiFlags: Int): EditorViewSnapshot {
    return if (bidiFlags == this.bidiFlags) {
      this
    } else {
      copy(bidiFlags = bidiFlags)
    }
  }

  fun withTabSize(tabSize: Int): EditorViewSnapshot {
    return if (tabSize == this.tabSize) {
      this
    } else {
      copy(tabSize = tabSize)
    }
  }

  fun withPaintCallback(paintCallback: Runnable?): EditorViewSnapshot {
    return if (paintCallback === this.paintCallback) {
      this
    } else {
      copy(paintCallback = paintCallback)
    }
  }

  fun withMetrics(metrics: EditorViewMetrics?): EditorViewSnapshot {
    return if (metrics === this.metrics) {
      this
    } else {
      copy(metrics = metrics)
    }
  }
}
