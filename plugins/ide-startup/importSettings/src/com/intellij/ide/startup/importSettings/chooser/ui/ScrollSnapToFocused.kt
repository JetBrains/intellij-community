// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

class ScrollSnapToFocused(private val component: JComponent, disposable: Disposable) : JBScrollPane(component) {
  init {
    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    border = JBUI.Borders.empty()
    minimumSize = Dimension(0, 0)

    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    val listener = PropertyChangeListener { e ->
        if (e.newValue != null) {
        val focussed = focusManager.focusOwner
        if(focussed != null && focussed is JComponent && SwingUtilities.isDescendingFrom(focussed, component)) {
            scrollViewPortIfNeeded(focussed)
        }
      }
    }

    focusManager.addPropertyChangeListener("focusOwner", listener)

    Disposer.register(disposable) {
      focusManager.removePropertyChangeListener(listener)
    }
  }

  private fun scrollViewPortIfNeeded(focused: JComponent) {
    if (component.isAncestorOf(focused)) {
      val viewPortLocationOnScreen = viewport.locationOnScreen
      val focusedBounds = focused.locationOnScreen
      val viewportWidth = viewport.width
      val viewportHeight = viewport.height
      val outOfViewport = (focusedBounds.y < viewPortLocationOnScreen.y
                           || focusedBounds.y + focused.height > viewPortLocationOnScreen.y + viewportHeight)

      if (outOfViewport) {
        focusedBounds.translate(-viewPortLocationOnScreen.x, -viewPortLocationOnScreen.y)
        focused.scrollRectToVisible(Rectangle(0, 0,
                                              viewportWidth,
                                              viewportHeight))
      }
    }
  }
}