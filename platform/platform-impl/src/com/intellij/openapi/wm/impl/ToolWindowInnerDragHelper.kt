// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.openapi.wm.impl.ToolWindowDragHelper.Companion.createDragImage
import com.intellij.openapi.wm.impl.ToolWindowDragHelper.Companion.createHighlighterComponent
import com.intellij.openapi.wm.impl.content.BaseLabel
import com.intellij.openapi.wm.impl.content.ContentTabLabel
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.ComponentUtil
import com.intellij.ui.MouseDragHelper
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.content.Content.TEMPORARY_REMOVED_KEY
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.ui.tabs.TabsUtil
import com.intellij.util.IconUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NotNull
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
  private var mySourceDecorator = null as InternalDecoratorImpl?
  private var myInitialIndex = -1
  private var myCurrentDecorator = null as InternalDecoratorImpl?
  private var myDraggingTab = null as ContentTabLabel?
  private var myDialog = null as MyDialog?
  private var myCurrentDropSide = -1
  private var myCurrentDropIndex = -1
  private val myHighlighter = createHighlighterComponent()
  private val myInitialOffset = Point()

  override fun canStartDragging(dragComponent: JComponent, dragComponentPoint: Point): Boolean {
    return getTab(RelativePoint(dragComponent, dragComponentPoint)) != null
  }

  fun getTab(point: RelativePoint): ContentTabLabel? {
    with(point.getPoint(pane)) {
      val child = SwingUtilities.getDeepestComponentAt(pane, x, y)
      val decorator = InternalDecoratorImpl.findTopLevelDecorator(child)
      return if (decorator != null
                 && UIUtil.isClientPropertyTrue(decorator.toolWindow.component, ToolWindowContentUi.ALLOW_DND_FOR_TABS)
                 && child is ContentTabLabel && child.parent is ToolWindowContentUi.TabPanel) child
      else null
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
      mySourceDecorator = null
      myDraggingTab = null
      return
    }
    myInitialOffset.location = relativePoint.getPoint(contentTabLabel)
    myDraggingTab = contentTabLabel
    mySourceDecorator = InternalDecoratorImpl.findNearestDecorator(contentTabLabel)
    myInitialIndex = contentTabLabel.getIndex()
    myCurrentDecorator = mySourceDecorator
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
    if (mySourceDecorator == null || myCurrentDecorator == null) return
    val content = myDraggingTab!!.content

    if (mySourceDecorator == myCurrentDecorator) {
      if (mySourceDecorator!!.contentManager.contentCount > 0) {
        mySourceDecorator!!.splitWithContent(content, myCurrentDropSide, myCurrentDropIndex - 1)
      } else {
        mySourceDecorator!!.contentManager.addContent(myDraggingTab!!.content)
      }
      return
    }

    content.putUserData(TEMPORARY_REMOVED_KEY, true)
    try {
      myCurrentDecorator!!.splitWithContent(content, myCurrentDropSide, myCurrentDropIndex - 1)
    } finally {
      content.putUserData(TEMPORARY_REMOVED_KEY, null)
    }

    if (mySourceDecorator!!.contentManager.isEmpty) {
      mySourceDecorator!!.unsplit(null)
    }
  }

  override fun cancelDragging(): Boolean {
    if (super.cancelDragging()) {
      with(myDraggingTab!!.content as ContentImpl) {
        val contentManager = mySourceDecorator!!.contentManager
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
    mySourceDecorator?.setSplitUnsplitInProgress(false)
    mySourceDecorator = null
    myDraggingTab = null
    myCurrentDecorator?.setDropInfoIndex(-1, 0)
    myCurrentDecorator = null
    val parent = myHighlighter.parent
    if (parent is JComponent) {
      parent.remove(myHighlighter)
      parent.revalidate()
      parent.repaint()
    }
    @Suppress("SSBasedInspection")
    myDialog?.dispose()
    myDialog = null
  }

  private fun startDrag() {
    if (mySourceDecorator == null || mySourceDecorator?.contentManager?.contentCount == 0 || myDialog == null) {
      return
    }
    val manager = myDraggingTab!!.content.manager
    if (manager != null) {
      with(myDraggingTab!!.content as ContentImpl) {
        this.putUserData(TEMPORARY_REMOVED_KEY, true)
        val index = manager.getIndexOfContent(this) + 1
        SwingUtilities.invokeLater {
          try {
            mySourceDecorator!!.setSplitUnsplitInProgress(true)
            manager.removeContent(this, false)
            mySourceDecorator!!.setDropInfoIndex(index, myDraggingTab!!.width)
          } finally {
            mySourceDecorator!!.setSplitUnsplitInProgress(false)
          }
        }
      }
    }

    with(IdeGlassPaneUtil.find(pane) as JComponent) {
      add(myHighlighter)
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
      myCurrentDropSide = TabsUtil.getDropSideFor(relativePoint.getPoint(myCurrentDecorator), myCurrentDecorator)
      val dropArea = Rectangle(myCurrentDecorator!!.size)
      TabsUtil.updateBoundsWithDropSide( dropArea, myCurrentDropSide)
      dropArea.bounds = SwingUtilities.convertRectangle(myCurrentDecorator!!, dropArea, pane.rootPane.glassPane)
      myCurrentDropIndex = getIndex(relativePoint)
      if (myCurrentDropIndex != -1) {
        myCurrentDecorator!!.setDropInfoIndex(myCurrentDropIndex, myDialog!!.width)
        myHighlighter.bounds = Rectangle()
      } else {
        myCurrentDecorator?.setDropInfoIndex(-1, 0)
        myHighlighter.bounds = dropArea
      }
    }
    else {
      myCurrentDropIndex = -1
      myHighlighter.bounds = Rectangle()
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

  class MyDialog(owner: JComponent,
                 val helper: ToolWindowInnerDragHelper,
                 val tabImage: BufferedImage) : JDialog(
    UIUtil.getWindow(owner), null, ModalityType.MODELESS) {
    var myDragOut = null as Boolean?

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