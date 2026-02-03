// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.border

import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import javax.swing.border.Border

@Internal
interface NamedBorder : Border {

  val name: String
  val original: Border

}

@get:Internal
val Border.name: String? get() = (this as? NamedBorder)?.name

@Internal
fun Border.withName(name: String): NamedBorder = NamedBorderImpl(name, this)

private class NamedBorderImpl(
  override val name: String,
  override val original: Border
) : NamedBorder {

  override fun paintBorder(c: Component?, g: Graphics?, x: Int, y: Int, width: Int, height: Int) {
    original.paintBorder(c, g, x, y, width, height)
  }

  override fun getBorderInsets(c: Component?): Insets = original.getBorderInsets(c)

  override fun isBorderOpaque(): Boolean = original.isBorderOpaque

  override fun toString() = "NamedBorderImpl(name='$name', original=$original)"

}
