// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.drag

import java.awt.Component
import java.awt.Dimension

/**
 * A bridge between tool window stripes API and [DragImageView].
 * 
 * There's some code in tool window stripes that performs some layout operations
 * based on the size and the preferred size on the dragged image.
 * The same code is used to layout actual components.
 * This interface provides an abstraction layer between this logic
 * and the image being dragged, to avoid coupling this logic with
 * implementations based strictly on actual components.
 */
internal interface DragButton {
  val size: Dimension
  val preferredSize: Dimension
  fun setBounds(x: Int, y: Int, width: Int, height: Int)
}

internal fun Component.asDragButton(): DragButton = ComponentAsDragButton(this)

private class ComponentAsDragButton(private val component: Component) : DragButton {
  override val size: Dimension
    get() = component.size

  override val preferredSize: Dimension
    get() = component.preferredSize

  override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
    component.setBounds(x,  y, width, height)
  }
}
