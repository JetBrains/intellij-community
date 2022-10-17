package org.jetbrains.completion.full.line.settings.ui.components

import com.intellij.ui.JBCardLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension

class DynamicCardLayout : JBCardLayout() {
  override fun preferredLayoutSize(parent: Container) = parent.layoutSize {
    preferredSize
  }

  override fun minimumLayoutSize(parent: Container) = parent.layoutSize {
    minimumSize
  }

  private fun Container.layoutSize(a: Component.() -> Dimension): Dimension {
    synchronized(treeLock) {
      val insets = insets
      val components = componentCount
      var w = 0
      var h = 0
      for (i in 0 until components) {
        val comp = getComponent(i)
        if (comp.isVisible) {
          val d = comp.a()
          if (d.width > w) {
            w = d.width
          }
          if (d.height > h) {
            h = d.height
          }
        }
      }
      return Dimension(
        insets.left + insets.right + w + hgap * 2,
        insets.top + insets.bottom + h + vgap * 2
      )
    }
  }
}
