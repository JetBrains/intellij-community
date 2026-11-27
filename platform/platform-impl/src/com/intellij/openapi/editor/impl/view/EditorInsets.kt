// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import java.awt.Insets

/**
 * The insets of the editor content component, in pixels.
 */
internal data class EditorInsets(
  private val top: Int,
  private val left: Int,
  private val bottom: Int,
  private val right: Int,
) {
  /**
   * Returns a copy that the caller owns.
   */
  @Suppress("UseDPIAwareInsets")
  fun toInsets(): Insets {
    return Insets(top, left, bottom, right)
  }
}
