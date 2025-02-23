// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.html

import java.awt.Component
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JComponent
import javax.swing.text.Element
import javax.swing.text.html.BlockView
import javax.swing.text.html.FormView
import javax.swing.text.html.InlineView

internal class FormViewEx(elem: Element) : FormView(elem) {

  override fun createComponent(): Component? =
    super.createComponent()?.apply {
      focusTraversalKeysEnabled = true
      isFocusable = true
      addFocusListener(object : FocusAdapter() {
        override fun focusGained(e: FocusEvent?) {
          (this@apply as? JComponent)?.scrollRectToVisible(bounds)
        }
      })
    }

  override fun getAlignment(axis: Int): Float {
    if (axis != Y_AXIS)
      return super.getAlignment(axis)
    val siblingsCount = parent.viewCount
    var myIndex = -1
    var siblingAlignment = -1f
    for (i in 0 until siblingsCount) {
      val child = parent.getView(i)
      if (child === this) {
        myIndex = i
      }
      else if (child is InlineView || child is BlockView) {
        siblingAlignment = child.getAlignment(axis)
        if (myIndex >= 0)
          break
      }
    }
    return siblingAlignment.takeIf { it >= 0 }
           ?: super.getAlignment(axis)
  }

}