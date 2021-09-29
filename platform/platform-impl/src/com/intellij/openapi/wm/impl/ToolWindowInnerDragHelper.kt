// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.openapi.wm.impl.ToolWindowDragHelper.Companion.createDragImage
import com.intellij.openapi.wm.impl.ToolWindowDragHelper.Companion.createHighlighterComponent
import com.intellij.openapi.wm.impl.content.ContentTabLabel
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.MouseDragHelper
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.awt.RelativeRectangle
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

internal class ToolWindowInnerDragHelper(parent: @NotNull Disposable, val decorator: @NotNull InternalDecoratorImpl)
  : MouseDragHelper<InternalDecoratorImpl>(parent, decorator) {
  private var mySourceDecorator = null as InternalDecoratorImpl?
  private var myInitialIndex = -1
  private var myCurrentDecorator = null as InternalDecoratorImpl?
  private var myDraggingTab = null as ContentTabLabel?
  private var myDialog = null as MyDialog?
  private var myCurrentDropSide = -1
  private val myHighlighter = createHighlighterComponent()
  private val myInitialOffset = Point()

  override fun canStartDragging(dragComponent: JComponent, dragComponentPoint: Point): Boolean {
    return getTab(RelativePoint(dragComponent, dragComponentPoint)) != null
  }

  fun getTab(point: RelativePoint): ContentTabLabel? {
    with(point.getPoint(decorator)) {
      val child = SwingUtilities.getDeepestComponentAt(decorator, x, y)
      return if (child is ContentTabLabel && child.parent is ToolWindowContentUi.TabPanel) child else null
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
    myInitialIndex = mySourceDecorator?.contentManager?.getIndexOfContent(contentTabLabel.content) ?: -1
    myCurrentDecorator = mySourceDecorator
    myDialog = MyDialog(decorator, this, createDragImage(contentTabLabel, -1))
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
        mySourceDecorator!!.splitWithContent(content, myCurrentDropSide)
      } else {
        mySourceDecorator!!.contentManager.addContent(myDraggingTab!!.content)
      }
      return
    }

    content.putUserData(TEMPORARY_REMOVED_KEY, true)
    try {
      myCurrentDecorator!!.splitWithContent(content, myCurrentDropSide)
    } finally {
      content.putUserData(TEMPORARY_REMOVED_KEY, null)
    }

    if (mySourceDecorator!!.contentManager.isEmpty) {
      mySourceDecorator!!.unsplit(content)
    }
  }

  override fun cancelDragging(): Boolean {
    if (super.cancelDragging()) {
      with(myDraggingTab!!.content as ContentImpl) {
        mySourceDecorator!!.contentManager.addContent(this, myInitialIndex)
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
      startDrag(event)
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

  private fun startDrag(event: MouseEvent) {
    if (mySourceDecorator == null || mySourceDecorator?.contentManager?.contentCount == 0 || myDialog == null) {
      return
    }
    with(IdeGlassPaneUtil.find(decorator) as JComponent) {
      add(myHighlighter)
      revalidate()
      repaint()
    }
    if (myDraggingTab!!.content.manager != null) {
      with(myDraggingTab!!.content as ContentImpl) {
        this.putUserData(TEMPORARY_REMOVED_KEY, true)
        SwingUtilities.invokeLater {
          try {
            mySourceDecorator!!.setSplitUnsplitInProgress(true)
            manager!!.removeContent(this, false)
          } finally {
            mySourceDecorator!!.setSplitUnsplitInProgress(false)
          }
        }
      }
    }

    myDialog?.isVisible = true
  }

  private fun relocate(event: MouseEvent) {
    val relativePoint = RelativePoint(event)
    myCurrentDecorator = getDecorator(relativePoint)
    val screenPoint = event.locationOnScreen
    myDialog!!.setLocation(screenPoint.x - myInitialOffset.x, screenPoint.y - myInitialOffset.y)
    if (myCurrentDecorator != null) {
      myCurrentDropSide = TabsUtil.getDropSideFor(relativePoint.getPoint(myCurrentDecorator), myCurrentDecorator)

      val dropArea = RelativeRectangle(myCurrentDecorator, Rectangle(myCurrentDecorator!!.size))
        .getRectangleOn(decorator.rootPane.glassPane)
      TabsUtil.updateBoundsWithDropSide(dropArea, myCurrentDropSide)
      myHighlighter.bounds = dropArea
    }
    else {
      myHighlighter.bounds = Rectangle()
    }
    with(decorator.rootPane?.glassPane as JComponent) {
      add(myHighlighter)
      revalidate()
      repaint()
    }
  }

  private fun getDecorator(relativePoint: RelativePoint): InternalDecoratorImpl? {
    val rootPane = decorator.rootPane
    if (rootPane is IdeRootPane) {
      val point = relativePoint.getPoint(rootPane)
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