// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.markup.TextAttributes

internal class EditorPrefix(
  @JvmField val prefixText: String?,
  @JvmField val prefixAttributes: TextAttributes?,
  @JvmField val prefixLayout: LineLayout?,
) {
  fun withPrefixLayout(prefixLayout: LineLayout?): EditorPrefix {
    return if (prefixLayout === this.prefixLayout) {
      this
    } else {
      EditorPrefix(prefixText, prefixAttributes, prefixLayout)
    }
  }
}
