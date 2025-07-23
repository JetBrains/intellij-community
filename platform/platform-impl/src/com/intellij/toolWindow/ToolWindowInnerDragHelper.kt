// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.openapi.wm.impl.content.BaseLabel
import com.intellij.openapi.wm.impl.content.ContentTabLabel
import com.intellij.openapi.wm.impl.content.SingleContentLayout
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.toolWindow.ToolWindowDragHelper.Companion.createDropTargetHighlightComponent
import com.intellij.toolWindow.ToolWindowDragHelper.Companion.createThumbnailDragImage
import com.intellij.ui.ComponentUtil
import com.intellij.ui.MouseDragHelper
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.content.Content.TEMPORARY_REMOVED_KEY
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.ui.content.impl.ContentManagerImpl
import com.intellij.ui.tabs.TabsUtil
import com.intellij.util.IconUtil
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Image
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.*

internal class ToolWindowInnerDragHelper(parent: Disposable, val pane: JComponent) : MouseDragHelper<JComponent>(parent, pane) {
  private var sourceDecorator = null as InternalDecoratorImpl?
  private var sourceTopDecorator: InternalDecoratorImpl? = null
  private var myInitialIndex = -1
  private var myCurrentDecorator = null as InternalDecoratorImpl?
  private var myDraggingTab = null as ContentTabLabel?
  private var myDialog = null as MyDialog?
  private var currentDropSide = -1
  private var currentDropIndex = -1
  private val highlighter = createDropTargetHighlightComponent()
  private val myInitialOffset = Point()

  override fun canStartDragging(dragComponent: JComponent, dragComponentPoint: Point): Boolean {
    return getTab(RelativePoint(dragComponent, dragComponentPoint)) != null
  }

  fun getTab(point: RelativePoint): ContentTabLabel? {
    with(point.getPoint(pane)) {
      val child = SwingUtilities.getDeepestComponentAt(pane, x, y)
      val decorator = InternalDecoratorImpl.findTopLevelDecorator(child)
      if (decorator != null &&
          ToolWindowContentUi.isTabsReorderingAllowed(decorator.toolWindow) &&
          child is ContentTabLabel &&
          (child.parent is ToolWindowContentUi.TabPanel ||
           Registry.`is`("debugger.new.tool.window.layout.dnd", false) && child.parent is SingleContentLayout.TabAdapter) &&
          (decorator.toolWindow.contentManager as ContentManagerImpl).getRecursiveContentCount() > 1) {
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
      sourceTopDecorator = null
      myDraggingTab = null
      return
    }
    myInitialOffset.location = relativePoint.getPoint(contentTabLabel)
    myDraggingTab = contentTabLabel
    sourceDecorator = InternalDecoratorImpl.findNearestDecorator(contentTabLabel)
    sourceTopDecorator = sourceDecorator?.let { findTopDecorator(it) }
    myInitialIndex = getInitialIndex(contentTabLabel)
    myCurrentDecorator = sourceDecorator
    myDialog = MyDialog(pane, this, createThumbnailDragImage(contentTabLabel, -1))
  }

  private fun getInitialIndex(tabLabel: ContentTabLabel): Int {
    val content = tabLabel.content
    return if (content is SingleContentLayout.SubContent && sourceDecorator?.isSingleContentLayout() == true) {
      content.supplier.getTabs().getIndexOf(content.info)
    }
    else content.manager!!.getIndexOfContent(content)
  }

  private fun getTabIndex(point: RelativePoint): Int {
    val p = point.getPoint(pane)
    val componentBelowCursor = SwingUtilities.getDeepestComponentAt(pane, p.x, p.y)
    val tabPanel = ComponentUtil.getParentOfType(ToolWindowContentUi.TabPanel::class.java, componentBelowCursor)
    if (tabPanel == null) {
      return -1
    }
    val tabAdapter = tabPanel.components.filterIsInstance<SingleContentLayout.TabAdapter>().singleOrNull()
    return if (tabAdapter != null && Registry.`is`("debugger.new.tool.window.layout.dnd", false)) {
      doGetTabIndex(tabAdapter, point)
    }
    else doGetTabIndex(tabPanel, point).coerceAtLeast(1)
  }

  private fun doGetTabIndex(tabContainer: JComponent, point: RelativePoint): Int {
    val p = point.getPoint(tabContainer)
    val draggingTab = myDraggingTab
    if (draggingTab != null) {
      // Make p the center of the tab being dragged.
      p.x += draggingTab.width / 2 - myInitialOffset.x
      p.y += draggingTab.height / 2 - myInitialOffset.y
    }
    // com.intellij.openapi.wm.impl.content.TabContentLayout.dropOverPlaceholder
    val placeholderIndex = tabContainer.components.indexOfLast { it is JLabel && it !is BaseLabel }
    for (i in 0 until tabContainer.componentCount) {
      val child = tabContainer.components[i]
      if (child !is ContentTabLabel) continue
      val childCenterX = child.x + child.width / 2
      if (placeholderIndex != -1) {
        if (i < placeholderIndex && p.x < childCenterX) {
          return i
        }
        if (i > placeholderIndex) {
          if (p.x > childCenterX) {
            return i.coerceAtMost(tabContainer.components.indexOfLast { it is JLabel })
          }
          return placeholderIndex
        }
      }
      if (child.bounds.contains(p)) return i
    }
    return tabContainer.components.indexOfLast { it is JLabel }.coerceAtLeast(0)
  }

  override fun mouseReleased(e: MouseEvent?) {
    super.mouseReleased(e)
    stopDrag()
  }

  override fun processDragFinish(event: MouseEvent, willDragOutStart: Boolean) {
    val sourceDecorator = sourceDecorator
    val curDecorator = myCurrentDecorator
    if (sourceDecorator == null || curDecorator == null) {
      return
    }

    val content = myDraggingTab!!.content
    val contentManager = sourceDecorator.contentManager

    if (content is SingleContentLayout.SubContent && curDecorator.isSingleContentLayout()) {
      val tabs = content.supplier.getTabs()
      val tabInfo = content.info
      if (currentDropIndex != -1) {
        tabs.removeTab(tabInfo)
        tabInfo.isHidden = false
        tabs.addTab(tabInfo, currentDropIndex)
      }
      else if (currentDropSide == -1 || currentDropSide == SwingConstants.CENTER) {
        tabInfo.isHidden = false
      }
      else {
        curDecorator.splitWithContent(content, currentDropSide, -1)
      }
    }
    else {
      fun splitWithContent(decorator: InternalDecoratorImpl) {
        val index = if (currentDropIndex != -1) (currentDropIndex - 1).coerceIn(0, decorator.contentManager.contentCount) else -1
        decorator.splitWithContent(content, currentDropSide, index)
      }

      if (sourceDecorator == curDecorator) {
        if (contentManager.contentCount > 0) {
          splitWithContent(sourceDecorator)
        }
        else {
          contentManager.addContent(content)
        }
        return
      }

      content.putUserData(TEMPORARY_REMOVED_KEY, true)
      try {
        splitWithContent(curDecorator)
      }
      finally {
        content.putUserData(TEMPORARY_REMOVED_KEY, null)
      }
    }

    if (contentManager.isEmpty) {
      sourceDecorator.unsplit(content)
    }
  }

  override fun cancelDragging(): Boolean {
    if (super.cancelDragging()) {
      val content = myDraggingTab!!.content
      if (content is SingleContentLayout.SubContent && sourceDecorator!!.isSingleContentLayout()) {
        content.info.isHidden = false
      }
      else {
        val contentManager = sourceDecorator!!.contentManager
        contentManager.addContent(content, myInitialIndex.coerceAtMost(contentManager.contentCount))
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

  private fun stopDrag() {
    if (myDraggingTab == null) return
    (myDraggingTab!!.content as ContentImpl).putUserData(TEMPORARY_REMOVED_KEY, null)
    sourceDecorator?.isSplitUnsplitInProgress = false
    sourceDecorator = null
    sourceTopDecorator = null
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
    val sourceDecorator = sourceDecorator
    if (sourceDecorator == null || sourceDecorator.contentManager.contentCount == 0 || myDialog == null) {
      return
    }

    val content = myDraggingTab!!.content
    content.putUserData(TEMPORARY_REMOVED_KEY, true)
    if (content is SingleContentLayout.SubContent && sourceDecorator.isSingleContentLayout()) {
      val tabs = content.supplier.getTabs()
      val tabInfo = content.info
      val index = tabs.getIndexOf(tabInfo)
      SwingUtilities.invokeLater {
        tabInfo.isHidden = true
        sourceDecorator.setDropInfoIndex(index, myDraggingTab!!.width)
      }
    }
    else {
      val manager = sourceDecorator.contentManager
      val index = manager.getIndexOfContent(content) + 1
      SwingUtilities.invokeLater {
        try {
          sourceDecorator.isSplitUnsplitInProgress = true
          manager.removeContent(content, false)
          sourceDecorator.setDropInfoIndex(index, myDraggingTab!!.width)
        }
        finally {
          sourceDecorator.isSplitUnsplitInProgress = false
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
      currentDropIndex = getTabIndex(relativePoint)
      if (currentDropIndex != -1) {
        myCurrentDecorator!!.setDropInfoIndex(currentDropIndex, myDialog!!.width)
        highlighter.bounds = Rectangle()
      }
      else {
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
    val topDecorator = sourceTopDecorator ?: return null
    val point = relativePoint.getPoint(topDecorator)
    val component = SwingUtilities.getDeepestComponentAt(topDecorator, point.x, point.y)
    return InternalDecoratorImpl.findNearestDecorator(component)
  }

  private fun findTopDecorator(component: Component): InternalDecoratorImpl? {
    return UIUtil.uiParents(component, false).lastOrNull { it is InternalDecoratorImpl } as? InternalDecoratorImpl
  }

  private fun InternalDecoratorImpl.isSingleContentLayout(): Boolean {
    return contentManager.contents.singleOrNull().let { it != null && it !is SingleContentLayout.SubContent }
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