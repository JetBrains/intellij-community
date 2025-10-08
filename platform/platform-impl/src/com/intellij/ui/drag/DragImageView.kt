// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.drag

import java.awt.Dimension
import java.awt.Image
import java.awt.Point
import java.awt.Rectangle

/**
 * An image being dragged.
 * 
 * This is an abstraction layer between ad-hoc DnD mechanisms
 * and the actual mechanism for displaying the image being dragged.
 * 
 * In most cases it's implemented using [DialogDragImageView], which wraps some undecorated dialog
 * with the image inside.
 * But for Wayland, because it doesn't support moving windows,
 * [GlassPaneDragImageView] is used instead.
 */
internal interface DragImageView {
  var location: Point

  val bounds: Rectangle
  val size: Dimension
  
  val preferredSize: Dimension
  
  var image: Image?
  
  fun show()
  fun hide()
  
  fun asDragButton(): DragButton = DragImageViewAsDragButton(this)
}

private class DragImageViewAsDragButton(private val view: DragImageView) : DragButton {
  override val size: Dimension
    get() = view.size

  override val preferredSize: Dimension
    get() = view.preferredSize

  override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
    throw UnsupportedOperationException("setBounds must only be called for buttons backed by a real component")
  }
}
