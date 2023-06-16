// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui

import java.awt.*
import kotlin.math.max
import kotlin.math.min

class SingleComponentCenteringLayout : LayoutManager2 {

  private var component: Component? = null

  override fun layoutContainer(parent: Container) {
    val component = component
    if (component == null) return

    component.bounds = getBoundsForCentered(parent, component)
  }

  override fun maximumLayoutSize(target: Container): Dimension = component?.maximumSize?.also { JBInsets.addTo(it, target.insets) }
                                                                 ?: Dimension(Int.MAX_VALUE / 2, Int.MAX_VALUE / 2)

  override fun preferredLayoutSize(parent: Container): Dimension = component?.preferredSize?.also { JBInsets.addTo(it, parent.insets) }
                                                                   ?: Dimension(0, 0)

  override fun minimumLayoutSize(parent: Container): Dimension = component?.minimumSize?.also { JBInsets.addTo(it, parent.insets) }
                                                                 ?: Dimension(0, 0)

  override fun addLayoutComponent(comp: Component?, constraints: Any?) {
    component = comp
  }

  override fun addLayoutComponent(name: String?, comp: Component?) {
    component = comp
  }

  override fun removeLayoutComponent(comp: Component?) {
    if (component == comp) component = null
  }

  override fun invalidateLayout(target: Container?) {}
  override fun getLayoutAlignmentY(target: Container?): Float = 0.5f
  override fun getLayoutAlignmentX(target: Container?): Float = 0.5f

  companion object {

    fun getBoundsForCentered(parent: Container, component: Component): Rectangle {
      val size: Dimension = parent.size
      val preferredSize: Dimension = component.preferredSize

      val insets: Insets = parent.insets
      JBInsets.removeFrom(size, insets)

      val x = max(0, insets.left + (size.width - preferredSize.width) / 2)
      val y = max(0, insets.top + (size.height - preferredSize.height) / 2)
      val width = min(size.width, preferredSize.width)
      val height = min(size.height, preferredSize.height)
      return Rectangle(x, y, width, height)
    }
  }
}