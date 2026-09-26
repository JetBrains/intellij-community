// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager2

/**
 * @author Alexander Lobas
 */
open class FinalLayoutWrapper(protected val layout: LayoutManager2) : LayoutManager2 {
  override fun addLayoutComponent(comp: Component, constraints: Any?) {
    layout.addLayoutComponent(comp, constraints)
  }

  override fun addLayoutComponent(name: String?, comp: Component) {
    layout.addLayoutComponent(name, comp)
  }

  override fun removeLayoutComponent(comp: Component) {
    layout.removeLayoutComponent(comp)
  }

  override fun layoutContainer(parent: Container) {
    layout.layoutContainer(parent)
  }

  override fun invalidateLayout(target: Container) {
    layout.invalidateLayout(target)
  }

  override fun preferredLayoutSize(parent: Container): Dimension = layout.preferredLayoutSize(parent)

  override fun minimumLayoutSize(parent: Container): Dimension = layout.minimumLayoutSize(parent)

  override fun maximumLayoutSize(target: Container): Dimension = layout.maximumLayoutSize(target)

  override fun getLayoutAlignmentX(target: Container): Float = layout.getLayoutAlignmentX(target)

  override fun getLayoutAlignmentY(target: Container): Float = layout.getLayoutAlignmentY(target)
}