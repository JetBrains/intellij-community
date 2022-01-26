// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.openapi.wm.impl.content.BaseLabel
import com.intellij.openapi.wm.impl.content.ContentTabLabel
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.toolWindow.ToolWindowDragHelper
import com.intellij.toolWindow.ToolWindowDragHelper.Companion.createDragImage
import com.intellij.toolWindow.ToolWindowDragHelper.Companion.createHighlighterComponent
import com.intellij.ui.ClientProperty
import com.intellij.ui.ComponentUtil
import com.intellij.ui.MouseDragHelper
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.content.Content.TEMPORARY_REMOVED_KEY
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.ui.tabs.TabsUtil
import com.intellij.util.IconUtil
import org.jetbrains.annotations.NotNull
import java.awt.Component
import java.awt.Image
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.SwingUtilities

internal class ToolWindowInnerDragHelper(parent: @NotNull Disposable, val pane: @NotNull ToolWindowsPane)
  : MouseDragHelper<ToolWindowsPane>(parent, pane) {
  private var sourceDecorator = null as InternalDecoratorImpl?
  private var myInitialIndex = -1
  private var myCurrentDecorator = null as InternalDecoratorImpl?
  private var myDraggingTab = null as ContentTabLabel?
  private var myDialog = null as MyDialog?
  private var currentDropSide = -1
  private var currentDropIndex = -1
  private val highlighter = createHighlighterComponent()
  private val myInitialOffset = Point()

  override fun canStartDragging(dragComponent: JComponent, dragComponentPoint: Point): Boolean {
    return getTab(RelativePoint(dragComponent, dragComponentPoint)) != null
  }

  fun getTab(point: RelativePoint): ContentTabLabel? {
    with(point.getPoint(pane)) {
      val child = SwingUtilities.getDeepestComponentAt(pane, x, y)
      val decorator = InternalDecoratorImpl.findTopLevelDecorator(child)
      if (decorator != null &&
          ClientProperty.isTrue(decorator.toolWindow.component as Component?, ToolWindowContentUi.ALLOW_DND_FOR_TABS) &&
          child is ContentTabLabel && child.parent is ToolWindowContentUi.TabPanel) {
        return child
      }
      else {
        return null
      }
    }
  }

  override fun canFinishDragging(component: JComponent, point: RelativePoint): Boolean {
    if (myCurrentDecorator == null) return false
    return myCurrentDecorator!!.contains(point.getPoint(myCurrentDecorator))
  }

  override fun processMousePressed(event: MouseEvent) {
    val relativePoint = RelativePoint(event)
    val contentTabLabel = getTab(relativePoint)
    if (contentTabLabel == null) {
      sourceDecorator = null
      myDraggingTab = null
      return
    }
    myInitialOffset.location = relativePoint.getPoint(contentTabLabel)
    myDraggingTab = contentTabLabel
    sourceDecorator = InternalDecoratorImpl.findNearestDecorator(contentTabLabel)
    myInitialIndex = contentTabLabel.getIndex()
    myCurrentDecorator = sourceDecorator
    myDialog = MyDialog(pane, this, createDragImage(contentTabLabel, -1))
  }

  fun ContentTabLabel.getIndex() = content.manager!!.getIndexOfContent(content)

  private fun getIndex(point: RelativePoint): Int {
    var p = point.getPoint(pane)
    val componentBelowCursor = SwingUtilities.getDeepestComponentAt(pane, p.x, p.y)
    val tabPanel = ComponentUtil.getParentOfType(ToolWindowContentUi.TabPanel::class.java, componentBelowCursor)
    if (tabPanel == null) {
      return -1
    }
    p = point.getPoint(tabPanel)
    var placeholderIndex = -1
    for (i in 0..tabPanel.componentCount - 1) {
      val child = tabPanel.components[i]
      if (child is JLabel && child !is BaseLabel) { //com.intellij.openapi.wm.impl.content.TabContentLayout.myDropOverPlaceholder
        placeholderIndex = i
      }
    }
    for (i in 0..tabPanel.componentCount - 1) {
      val child = tabPanel.components[i]
      if (child is BaseLabel && child !is ContentTabLabel) {
        continue//See com.intellij.openapi.wm.impl.content.ContentLayout.myIdLabel
      }
      val childBounds = child.bounds
      if (placeholderIndex != -1 && i < placeholderIndex) {
        if (p.x < childBounds.minX) {
          return i.coerceAtLeast(1)
        }
      }
      if (placeholderIndex != -1 && i > placeholderIndex) {
        if (p.x > childBounds.maxX)
          return (i + 1).coerceAtMost(tabPanel.componentCount - 1)
        else
          return placeholderIndex
      }
      if (childBounds.contains(p))
        return i
    }
    return tabPanel.componentCount - 1
  }

  override fun mouseReleased(e: MouseEvent?) {
    super.mouseReleased(e)
    stopDrag()
  }

  override fun processDragFinish(event: MouseEvent, willDragOutStart: Boolean) {
    if (sourceDecorator == null || myCurrentDecorator == null) {
      return
    }

    val content = myDraggingTab!!.content

    val contentManager = sourceDecorator!!.contentManager!!
    if (sourceDecorator == myCurrentDecorator) {
      if (contentManager.contentCount > 0) {
        sourceDecorator!!.splitWithContent(content, currentDropSide, currentDropIndex - 1)
      }
      else {
        contentManager.addContent(myDraggingTab!!.content)
      }
      return
    }

    content.putUserData(TEMPORARY_REMOVED_KEY, true)
    try {
      myCurrentDecorator!!.splitWithContent(content, currentDropSide, currentDropIndex - 1)
    }
    finally {
      content.putUserData(TEMPORARY_REMOVED_KEY, null)
    }

    if (contentManager.isEmpty) {
      sourceDecorator!!.unsplit(null)
    }
  }

  override fun cancelDragging(): Boolean {
    if (super.cancelDragging()) {
      with(myDraggingTab!!.content as ContentImpl) {
        val contentManager = sourceDecorator!!.contentManager!!
        contentManager.addContent(this, myInitialIndex.coerceAtMost(contentManager.contentCount))
        putUserData(TEMPORARY_REMOVED_KEY, null)
      }
      stopDrag()
      return true
    }
    return false
  }

  override fun stop() {
    super.stop()
    stopDrag()
  }

  override fun processDrag(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point) {
    if (isDragJustStarted) {
      startDrag()
    }
    else {
      relocate(event)
    }
  }

  fun stopDrag() {
    if (myDraggingTab == null) return
    (myDraggingTab!!.content as ContentImpl).putUserData(TEMPORARY_REMOVED_KEY, null)
    sourceDecorator?.setSplitUnsplitInProgress(false)
    sourceDecorator = null
    myDraggingTab = null
    myCurrentDecorator?.setDropInfoIndex(-1, 0)
    myCurrentDecorator = null
    val parent = highlighter.parent
    if (parent is JComponent) {
      parent.remove(highlighter)
      parent.revalidate()
      parent.repaint()
    }
    @Suppress("SSBasedInspection")
    myDialog?.dispose()
    myDialog = null
  }

  private fun startDrag() {
    if (sourceDecorator == null || sourceDecorator?.contentManager?.contentCount == 0 || myDialog == null) {
      return
    }
    val manager = myDraggingTab!!.content.manager
    if (manager != null) {
      with(myDraggingTab!!.content as ContentImpl) {
        this.putUserData(TEMPORARY_REMOVED_KEY, true)
        val index = manager.getIndexOfContent(this) + 1
        SwingUtilities.invokeLater {
          try {
            sourceDecorator!!.setSplitUnsplitInProgress(true)
            manager.removeContent(this, false)
            sourceDecorator!!.setDropInfoIndex(index, myDraggingTab!!.width)
          } finally {
            sourceDecorator!!.setSplitUnsplitInProgress(false)
          }
        }
      }
    }

    with(IdeGlassPaneUtil.find(pane) as JComponent) {
      add(highlighter)
      revalidate()
      repaint()
    }
  }

  private fun relocate(event: MouseEvent) {
    val relativePoint = RelativePoint(event)
    val tmp = getDecorator(relativePoint)
    if (myCurrentDecorator != tmp) {
      myCurrentDecorator?.setDropInfoIndex(-1, 0)
    }
    myCurrentDecorator = tmp

    val screenPoint = event.locationOnScreen
    myDialog!!.setLocation(screenPoint.x - myInitialOffset.x, screenPoint.y - myInitialOffset.y)
    myDialog?.isVisible = true

    if (myCurrentDecorator != null) {
      currentDropSide = TabsUtil.getDropSideFor(relativePoint.getPoint(myCurrentDecorator), myCurrentDecorator)
      val dropArea = Rectangle(myCurrentDecorator!!.size)
      TabsUtil.updateBoundsWithDropSide(dropArea, currentDropSide)
      dropArea.bounds = SwingUtilities.convertRectangle(myCurrentDecorator!!, dropArea, pane.rootPane.glassPane)
      currentDropIndex = getIndex(relativePoint)
      if (currentDropIndex != -1) {
        myCurrentDecorator!!.setDropInfoIndex(currentDropIndex, myDialog!!.width)
        highlighter.bounds = Rectangle()
      } else {
        myCurrentDecorator?.setDropInfoIndex(-1, 0)
        highlighter.bounds = dropArea
      }
    }
    else {
      currentDropIndex = -1
      highlighter.bounds = Rectangle()
    }
  }

  private fun getDecorator(relativePoint: RelativePoint): InternalDecoratorImpl? {
    val rootPane = pane.rootPane
    if (rootPane is IdeRootPane) {
      val point = relativePoint.getPoint(rootPane.toolWindowPane)
      val component = SwingUtilities.getDeepestComponentAt(rootPane.toolWindowPane, point.x, point.y)
      return InternalDecoratorImpl.findNearestDecorator(component)
    }
    return null
  }

  private class MyDialog(owner: JComponent, val helper: ToolWindowInnerDragHelper, tabImage: BufferedImage)
    : JDialog(ComponentUtil.getWindow(owner), null, ModalityType.MODELESS) {
    init {
      isUndecorated = true
      try {
        opacity = ToolWindowDragHelper.THUMB_OPACITY
      }
      catch (ignored: Exception) {
      }
      isAlwaysOnTop = true
      contentPane = JLabel(IconUtil.createImageIcon(tabImage as Image))
      contentPane.addMouseListener(object : MouseAdapter() {
        override fun mouseReleased(e: MouseEvent?) {
          helper.mouseReleased(e) //stop drag
        }

        override fun mouseDragged(e: MouseEvent?) {
          helper.relocate(e!!)
        }
      })
      pack()
    }
  }
}