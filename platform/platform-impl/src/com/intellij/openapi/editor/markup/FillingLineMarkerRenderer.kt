// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus.Experimental
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.Icon

@Experimental
interface FillingLineMarkerRenderer : LineMarkerRendererEx {
  override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
    val color = editor.colorsScheme.getAttributes(getTextAttributesKey())
    var bgColor = color.backgroundColor
    if (bgColor == null) {
      bgColor = color.foregroundColor
    }
    if (bgColor != null) {
      g.color = bgColor
      val w = getMaxWidth()?.let { r.width.coerceAtMost(JBUI.scale(it)) } ?: r.width
      g.fillRect(r.x, r.y, w, r.height)
    }
    val icon = getIcon()
    icon?.paintIcon(editor.component, g, r.x, r.y)
  }

  fun getIcon(): Icon? = null

  fun getTextAttributesKey(): TextAttributesKey

  fun getMaxWidth(): Int? = null
}