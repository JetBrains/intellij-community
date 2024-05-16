// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.stickyLines

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.ui.components.JBLayeredPane

internal class StickyLineComponents(private val editor: EditorEx, private val layeredPane: JBLayeredPane) {
  private val components: MutableList<StickyLineComponent> = mutableListOf()

  fun components(): Sequence<StickyLineComponent> {
    return components.asSequence().filter { !it.isEmpty() }
  }

  fun unboundComponents(): Sequence<StickyLineComponent> {
    return Sequence {
      object : Iterator<StickyLineComponent> {
        private var index = 0

        override fun hasNext(): Boolean {
          return true
        }

        override fun next(): StickyLineComponent {
          val component: StickyLineComponent
          if (index < components.size) {
            component = components[index]
          } else {
            component = StickyLineComponent(editor)
            layeredPane.add(component, (200 - components.size) as Any)
            components.add(component)
          }
          index++
          return component
        }
      }
    }
  }

  fun resetAfterIndex(index: Int) {
    for (i in index..components.lastIndex) {
      components[i].resetLine()
      components[i].isVisible = false
    }
  }

  fun clear(): Boolean {
    if (components.isEmpty()) {
      return false
    }
    components.clear()
    layeredPane.removeAll()
    return true
  }
}
