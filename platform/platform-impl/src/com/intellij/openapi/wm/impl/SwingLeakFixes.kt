// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.dnd.SmoothAutoScroller
import com.intellij.ui.treeStructure.Tree
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.ToolTipManager


internal fun fixSwingLeaks() {
  fixDragRecognitionSupportLeak()
  fixSmoothAutoScrollerDragListenerLeak()
  fixTooltipManagerLeak()
}

private fun fixDragRecognitionSupportLeak() {
  // sending a "mouse release" event to any DnD-supporting component indirectly calls javax.swing.plaf.basic.DragRecognitionSupport.clearState,
  // cleaning up the potential leak (that can happen if the user started dragging something and released the mouse outside the component)
  val fakeTree = object : Tree() {
    fun releaseDND() {
      processMouseEvent(mouseEvent(this, MouseEvent.MOUSE_RELEASED))
    }
  }
  fakeTree.dragEnabled = true
  fakeTree.releaseDND()
}

private fun fixSmoothAutoScrollerDragListenerLeak() {
  SmoothAutoScroller.recreateDragListener()
}

private fun fixTooltipManagerLeak() {
  val fakeComponent = JPanel()
  ToolTipManager.sharedInstance().mousePressed(mouseEvent(fakeComponent, MouseEvent.MOUSE_PRESSED))
}

private fun mouseEvent(source: Component, id: Int) = MouseEvent(
  source,
  id,
  System.currentTimeMillis(),
  0,
  0,
  0,
  1,
  false,
  MouseEvent.BUTTON1
)

