// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import java.awt.*
import kotlin.math.max

/**
 * Put two scrollable panels in a vertical stack.
 * If both panels preferred height is bigger than `panel.size / 2`, give them both equal height.
 */
internal class StagePopupVerticalLayout : LayoutManager {
  override fun addLayoutComponent(name: String?, comp: Component) = Unit
  override fun removeLayoutComponent(comp: Component) = Unit

  override fun preferredLayoutSize(parent: Container): Dimension = computeLayoutSize(parent) { it.preferredSize }
  override fun minimumLayoutSize(parent: Container): Dimension = computeLayoutSize(parent) { it.minimumSize }

  private fun computeLayoutSize(parent: Container, sizeFun: (Component) -> Dimension): Dimension {
    var width = 0
    var height = 0
    for (component in parent.components) {
      val size = sizeFun(component)
      width = max(width, size.width)
      height += size.height
    }
    return Dimension(width, height)
  }

  override fun layoutContainer(parent: Container) {
    assert(parent.componentCount == 2)
    val size = parent.size
    val panel1 = parent.getComponent(0)
    val panel2 = parent.getComponent(1)

    val height = size.height
    val prefHeight1 = panel1.preferredSize.height
    val prefHeight2 = panel2.preferredSize.height

    val height1: Int
    val height2: Int

    val isBig1 = prefHeight1 > height / 2
    val isBig2 = prefHeight2 > height / 2
    if (isBig1 && isBig2) {
      // split panel in half
      height1 = height / 2
      height2 = height - height1
    }
    else if (isBig1) {
      // scrollbar for panel1
      height2 = prefHeight2
      height1 = height - height2
    }
    else if (isBig2) {
      // scrollbar for panel2
      height1 = prefHeight1
      height2 = height - height1
    }
    else {
      // no scrollbar necessary
      height1 = prefHeight1
      height2 = prefHeight2
    }

    panel1.bounds = Rectangle(0, 0, size.width, height1)
    panel2.bounds = Rectangle(0, height1, size.width, height2)
  }
}