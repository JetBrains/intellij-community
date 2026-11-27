// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view

import com.intellij.openapi.editor.impl.EditorImpl
import java.awt.Color

/**
 * The two background colors of an editor: the default background of its color scheme, and its own background.
 * A background of either color is no background, so the paint fills nothing for it.
 */
internal class DefaultBackgrounds(
  private val schemeBackground: Color,
  private val editorBackground: Color,
) {
  /**
   * Says that a background of this color is no background.
   */
  fun isDefault(color: Color): Boolean {
    return color == schemeBackground || color == editorBackground
  }

  /**
   * Gives the number of layers that the user can see: the layers without the tail of default backgrounds.
   * The list starts at the top layer.
   */
  fun visibleCount(layers: List<BackgroundLayer>): Int {
    var count = layers.size
    while (count > 0 && isDefault(layers[count - 1].color)) {
      count--
    }
    return count
  }

  companion object {
    /**
     * Gives the default backgrounds of this editor.
     */
    @JvmStatic
    fun of(editor: EditorImpl): DefaultBackgrounds {
      return DefaultBackgrounds(editor.colorsScheme.defaultBackground, editor.backgroundColor)
    }
  }
}
