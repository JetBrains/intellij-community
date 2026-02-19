// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.render

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.AntialiasingType
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.ui.FontInfo
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.RenderingHints
import javax.swing.ListCellRenderer

@ApiStatus.Experimental
fun fontInfoRenderer(isEditorFont: Boolean): ListCellRenderer<Any?> {
  return listCellRenderer {
    @Suppress("HardCodedStringLiteral")
    val text = value?.toString() ?: ""
    val f = (value as? FontInfo)?.getFont(list.font.getSize())
    val isDisplayable = f?.canDisplayUpTo(text) == -1
    val additionalRenderingHints = mapOf(RenderingHints.KEY_TEXT_ANTIALIASING to AntialiasingType.getKeyForCurrentScope(isEditorFont))

    // Calculating preferred width can be quite consuming though (in particular, when a large number of fonts is available),
    // so we avoid such a calculation here.
    rowWidth = JBUI.scale(50)

    text(text) {
      if (isDisplayable) {
        font = f
      }
      renderingHints = additionalRenderingHints
    }

    if (!isDisplayable) {
      text(IdeBundle.message("font.info.renderer.non.latin")) {
        foreground = greyForeground
        renderingHints = additionalRenderingHints
      }
    }
  }
}
