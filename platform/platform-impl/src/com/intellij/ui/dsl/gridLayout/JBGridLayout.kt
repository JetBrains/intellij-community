// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.gridLayout

import org.jetbrains.annotations.ApiStatus
import java.awt.*

@ApiStatus.Experimental
class JBGridLayout : LayoutManager2 {

  val rootGrid: JBGrid
    get() = _rootGrid

  private val _rootGrid = JBGridImpl()

  override fun addLayoutComponent(comp: Component?, constraints: Any?) {
    val jbConstraints = checkConstraints(constraints)

    (jbConstraints.grid as JBGridImpl).register(checkComponent(comp), jbConstraints)
  }

  fun addLayoutSubGrid(constraints: JBConstraints): JBGrid {
    return (constraints.grid as JBGridImpl).registerSubGrid(constraints)
  }

  override fun addLayoutComponent(name: String?, comp: Component?) {
    throw UiDslException("Method addLayoutComponent(name: String?, comp: Component?) is not supported")
  }

  override fun removeLayoutComponent(comp: Component?) {
    if (!_rootGrid.unregister(checkComponent(comp))) {
      throw UiDslException("Component has not been registered: $comp")
    }
  }

  override fun preferredLayoutSize(parent: Container?): Dimension {
    if (parent == null) {
      throw UiDslException("Parent is null")
    }

    synchronized(parent.treeLock) {
      val preferredSize = _rootGrid.getPreferredSize()
      val insets = parent.insets
      return Dimension(
        preferredSize.width + insets.left + insets.right,
        preferredSize.height + insets.top + insets.bottom
      )
    }
  }

  override fun minimumLayoutSize(parent: Container?): Dimension {
    // May be we need to implement more accurate calculation later
    return preferredLayoutSize(parent)
  }

  override fun maximumLayoutSize(target: Container?) =
    Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE)

  override fun layoutContainer(parent: Container?) {
    if (parent == null) {
      throw UiDslException("Parent is null")
    }

    synchronized(parent.treeLock) {
      val insets = parent.insets
      val rect = Rectangle(
        insets.left, insets.top,
        parent.width - insets.left - insets.right,
        parent.height - insets.top - insets.bottom
      )

      _rootGrid.calculateLayoutData(rect.width, rect.height)
      _rootGrid.layout(rect)
    }
  }

  override fun getLayoutAlignmentX(target: Container?) =
    // Just like other layout managers, no special meaning here
    0.5f

  override fun getLayoutAlignmentY(target: Container?) =
    // Just like other layout managers, no special meaning here
    0.5f

  override fun invalidateLayout(target: Container?) {
    // Nothing to do
  }
}
