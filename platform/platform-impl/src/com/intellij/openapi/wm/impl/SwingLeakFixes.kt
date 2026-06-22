// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.dnd.SmoothAutoScroller
import com.intellij.ui.treeStructure.Tree
import java.awt.Component
import java.awt.Point
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetEvent
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.ToolTipManager
import javax.swing.TransferHandler
import javax.swing.UIManager


internal fun fixSwingLeaks() {
  fixDragRecognitionSupportLeak()
  fixDropHandlerLeak()
  fixSmoothAutoScrollerDragListenerLeak()
  fixTooltipManagerLeak()
  fixTreeUiBaselineComponentLeak()
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

private fun fixDropHandlerLeak() {
  // Need a regular JTree here so it uses the default drop listener, not our "smooth autoscroll" one.
  @Suppress("UndesirableClassUsage") val fakeTree = JTree()
  fakeTree.dragEnabled = true
  fakeTree.transferHandler = TransferHandler(null) // this call creates the default drop target
  // Clean up the global DropHandler, created in javax.swing.TransferHandler.getDropTargetListener.
  // Its support.component can contain a reference to literally anything.
  // This will replace DropHandler.support.component with our harmless tree:
  fakeTree.dropTarget.dragEnter(DropTargetDragEvent(
    fakeTree.dropTarget.dropTargetContext,
    Point(0, 0),
    DnDConstants.ACTION_COPY,
    DnDConstants.ACTION_COPY
  ))
  // And this will clean up DropHandler.component:
  fakeTree.dropTarget.dragExit(DropTargetEvent(fakeTree.dropTarget.dropTargetContext))
}

private fun fixSmoothAutoScrollerDragListenerLeak() {
  SmoothAutoScroller.recreateDragListener()
}

private fun fixTooltipManagerLeak() {
  val fakeComponent = JPanel()
  ToolTipManager.sharedInstance().mousePressed(mouseEvent(fakeComponent, MouseEvent.MOUSE_PRESSED))
}

private fun fixTreeUiBaselineComponentLeak() {
  val lafDefaults = UIManager.getLookAndFeelDefaults()
  synchronized(lafDefaults) { // prevent CME if someone accesses it from outside the EDT
    val iterator = lafDefaults.iterator()
    while (iterator.hasNext()) {
      val entry = iterator.next()
      if (entry.key.toString() == "Tree.baselineComponent") {
        iterator.remove()
        break
      }
    }
  }
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

