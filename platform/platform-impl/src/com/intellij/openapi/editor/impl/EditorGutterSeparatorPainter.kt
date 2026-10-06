// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics2D

@ApiStatus.Experimental
@ApiStatus.Internal
object EditorGutterSeparatorPainter {
  /** Paints the gutter whitespace separator in the given vertical range when it is visible. */
  fun paint(gutter: EditorGutterComponentEx, g: Graphics2D, y: Int, height: Int) {
    if (gutter is EditorGutterComponentImpl) {
      gutter.paintWhitespaceSeparator(g, y, height)
    }
  }
}
