// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.ide.DataManager
import com.intellij.idea.AppMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.ui.popup.PopupCornerType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.openapi.wm.impl.content.BaseLabel
import com.intellij.openapi.wm.impl.content.ContentTabLabel
import com.intellij.openapi.wm.impl.content.SingleContentLayout
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.openapi.wm.impl.content.ToolWindowInEditorSupport
import com.intellij.toolWindow.ToolWindowDragHelper.Companion.createDropTargetHighlightComponent
import com.intellij.toolWindow.ToolWindowDragHelper.Companion.createThumbnailDragImage
import com.intellij.ui.ComponentUtil
import com.intellij.ui.MouseDragHelper
import com.intellij.ui.WindowRoundedCornersManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.content.Content
import com.intellij.ui.content.Content.TEMPORARY_REMOVED_KEY
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.ui.content.impl.ContentManagerImpl
import com.intellij.ui.drag.DialogDragImageView
import com.intellij.ui.drag.DragImageView
import com.intellij.ui.drag.GlassPaneDragImageView
import com.intellij.ui.tabs.TabsUtil
import com.intellij.util.IconUtil
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
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
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

internal class ToolWindowInnerDragHelper(parent: Disposable, val pane: JComponent) : MouseDragHelper<JComponent>(parent, pane) {
  private var sourceDecorator = null as InternalDecoratorImpl?
  private var sourceTopDecorator: InternalDecoratorImpl? = null
  private var myInitialIndex = -1

  private var curDropLocation: DropLocation? = null
  private var myDraggingTab = null as ContentTabLabel?
  private var dragImageView: DragImageView? = null
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
      val editorSupport = getEditorSupport(decorator)
      if (decorator != null &&
          (canReorderTabs(decorator) || decorator.toolWindow.canSplitTabs()) &&
          child is ContentTabLabel &&
          (child.parent is ToolWindowContentUi.TabPanel ||
           Registry.`is`("debugger.new.tool.window.layout.dnd", false) && child.parent is SingleContentLayout.TabAdapter) &&
          ((decorator.toolWindow.contentManager as ContentManagerImpl).getRecursiveContentCount() > 1 ||
           editorSupport?.canOpenInEditor(decorator.toolWindow.project, child.content) == true)
      ) {
        return child
      }
      else {
        return null
      }
    }
  }

  override fun canFinishDragging(component: JComponent, point: RelativePoint): Boolean {
    val curLocation = curDropLocation
    return when (curLocation) {
      is DropLocation.ToolWindow -> {
        val component = curLocation.decorator
        val canDrop = currentDropIndex != -1 && canReorderTabs(component) || curLocation.decorator.toolWindow.canSplitTabs()
        component.contains(point.getPoint(component)) && canDrop
      }
      is DropLocation.Editor -> {
        val component = curLocation.window.component
        component.contains(point.getPoint(component))
      }
      else -> false
    }
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
    if (sourceDecorator != null) {
      curDropLocation = DropLocation.ToolWindow(sourceDecorator!!)
    }
    val tabImage = createThumbnailDragImage(contentTabLabel, -1)
    dragImageView = if (StartupUiUtil.isWaylandToolkit()) {
      GlassPaneDragImageView(IdeGlassPaneUtil.find(pane)).apply {
        image = tabImage
      }
    }
    else {
      DialogDragImageView(MyDialog(pane, this, tabImage))
    }
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
    val curLocation = curDropLocation
    if (sourceDecorator == null || curLocation == null) {
      return
    }

    val content = myDraggingTab!!.content

    when (curLocation) {
      is DropLocation.ToolWindow -> {
        dropIntoToolWindow(content, sourceDecorator, curLocation.decorator)
      }
      is DropLocation.Editor -> {
        dropIntoEditor(content, sourceDecorator, curLocation.window)
      }
    }

    if (sourceDecorator.contentManager.isEmpty) {
      sourceDecorator.unsplit(content)
    }
    val toolWindow = sourceDecorator.toolWindow
    if (toolWindow.contentManager.contentsRecursively.isEmpty()) {
      toolWindow.hide()
    }
  }

  private fun dropIntoToolWindow(
    content: Content,
    sourceDecorator: InternalDecoratorImpl,
    targetDecorator: InternalDecoratorImpl,
  ) {
    val contentManager = sourceDecorator.contentManager

    if (content is SingleContentLayout.SubContent && targetDecorator.isSingleContentLayout()) {
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
        targetDecorator.splitWithContent(content, currentDropSide, -1)
      }
    }
    else {
      fun splitWithContent(decorator: InternalDecoratorImpl) {
        val index = if (currentDropIndex != -1) (currentDropIndex - 1).coerceIn(0, decorator.contentManager.contentCount) else -1
        decorator.splitWithContent(content, currentDropSide, index)
      }

      if (sourceDecorator == targetDecorator) {
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
        splitWithContent(targetDecorator)
      }
      finally {
        content.putUserData(TEMPORARY_REMOVED_KEY, null)
      }
    }
  }

  private fun dropIntoEditor(content: Content, sourceDecorator: InternalDecoratorImpl, editorWindow: EditorWindow) {
    val support = getEditorSupport(sourceDecorator) ?: return
    // The support should extract the toolWindow-specific component from the content object and open it in the editor.
    support.openInEditor(content, editorWindow)
    // Now, the tab is not showing in the Tool Window, so let's dispose the content.
    Disposer.dispose(content)
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
    val curLocation = curDropLocation
    if (curLocation is DropLocation.ToolWindow) {
      curLocation.decorator.setDropInfoIndex(-1, 0)
    }
    curDropLocation = null
    val parent = highlighter.parent
    if (parent is JComponent) {
      parent.remove(highlighter)
      parent.revalidate()
      parent.repaint()
    }
    @Suppress("SSBasedInspection")
    dragImageView?.hide()
    dragImageView = null
  }

  private fun startDrag() {
    val sourceDecorator = sourceDecorator
    if (sourceDecorator == null || sourceDecorator.contentManager.contentCount == 0 || dragImageView == null) {
      return
    }

    val tab = myDraggingTab!!
    val content = tab.content
    content.putUserData(TEMPORARY_REMOVED_KEY, true)
    if (content is SingleContentLayout.SubContent && sourceDecorator.isSingleContentLayout()) {
      val tabs = content.supplier.getTabs()
      val tabInfo = content.info
      val index = tabs.getIndexOf(tabInfo)
      SwingUtilities.invokeLater {
        if (tab != myDraggingTab) {
          return@invokeLater  // no more actual
        }

        tabInfo.isHidden = true
        sourceDecorator.setDropInfoIndex(index, tab.width)
      }
    }
    else {
      val manager = sourceDecorator.contentManager
      val index = manager.getIndexOfContent(content) + 1
      invokeLater {
        if (tab != myDraggingTab) {
          return@invokeLater  // no more actual
        }

        try {
          sourceDecorator.isSplitUnsplitInProgress = true
          WriteIntentReadAction.run {
            manager.removeContent(content, false)
          }
          sourceDecorator.setDropInfoIndex(index, tab.width)
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
    // Relocate the dragging tab image
    val screenPoint = event.locationOnScreen
    dragImageView!!.location = Point(screenPoint.x - myInitialOffset.x, screenPoint.y - myInitialOffset.y)
    dragImageView!!.show()

    val relativePoint = RelativePoint(event)
    updateCurDropLocation(relativePoint)

    val dropLocation = curDropLocation
    when (dropLocation) {
      is DropLocation.ToolWindow -> highlightToolWindowDropArea(dropLocation.decorator, relativePoint)
      is DropLocation.Editor -> highlightEditorDropArea(dropLocation.window)
      else -> {
        currentDropIndex = -1
        highlighter.bounds = Rectangle()
      }
    }
  }

  private fun updateCurDropLocation(point: RelativePoint) {
    val decorator = findToolWindowDecorator(point)
    val editorWindow = findEditorWindow(point)

    curDropLocation?.let { dropLocation ->
      if (dropLocation is DropLocation.ToolWindow && dropLocation.decorator != decorator) {
        dropLocation.decorator.setDropInfoIndex(-1, 0)
      }
    }

    val content = myDraggingTab?.content
    curDropLocation = when {
      decorator != null && decorator == sourceDecorator && canReorderTabs(decorator) -> {
        // Drop into the same tool window decorator - always allowed.
        DropLocation.ToolWindow(decorator)
      }
      decorator != null && decorator.toolWindow.canSplitTabs() -> {
        // Drop into another decorator of the tool window - allowed only if the tool window allows tab splits.
        DropLocation.ToolWindow(decorator)
      }
      editorWindow != null && content != null && getEditorSupport(sourceDecorator)?.canOpenInEditor(editorWindow.manager.project, content) == true -> {
        // Drop into the editor - allowed only if the tool window provides necessary support.
        DropLocation.Editor(editorWindow)
      }
      else -> null
    }
  }

  private fun findToolWindowDecorator(relativePoint: RelativePoint): InternalDecoratorImpl? {
    val topDecorator = sourceTopDecorator ?: return null
    val point = relativePoint.getPoint(topDecorator)
    val component = SwingUtilities.getDeepestComponentAt(topDecorator, point.x, point.y)
    return InternalDecoratorImpl.findNearestDecorator(component)
  }

  private fun findEditorWindow(point: RelativePoint): EditorWindow? {
    val rootComponent = UIUtil.getRootPane(point.component)?.contentPane
    val originalPoint = point.getPoint(rootComponent)
    val component = SwingUtilities.getDeepestComponentAt(rootComponent, originalPoint.x, originalPoint.y)
    val dataContext = DataManager.getInstance().getDataContext(component)
    return dataContext.getData(EditorWindow.DATA_KEY)
  }

  private fun highlightToolWindowDropArea(decorator: InternalDecoratorImpl, point: RelativePoint) {
    currentDropIndex = getTabIndex(point)
    if (currentDropIndex != -1 && (canReorderTabs(decorator) || decorator.toolWindow.canSplitTabs())) {
      decorator.setDropInfoIndex(currentDropIndex, dragImageView!!.size.width)
      currentDropSide = -1
      highlighter.bounds = Rectangle()
    }
    else if (decorator.toolWindow.canSplitTabs()) {
      currentDropSide = TabsUtil.getDropSideFor(point.getPoint(decorator), decorator)
      val dropArea = Rectangle(decorator.size)
      TabsUtil.updateBoundsWithDropSide(dropArea, currentDropSide)
      dropArea.bounds = SwingUtilities.convertRectangle(decorator, dropArea, pane.rootPane.glassPane)

      decorator.setDropInfoIndex(-1, 0)
      highlighter.bounds = dropArea
    }
    else {
      decorator.setDropInfoIndex(-1, 0)
      currentDropIndex = -1
      currentDropSide = -1
      highlighter.bounds = Rectangle()
    }
  }

  private fun highlightEditorDropArea(window: EditorWindow) {
    val component = window.component
    val dropArea = Rectangle(component.size)
    dropArea.bounds = SwingUtilities.convertRectangle(component, dropArea, pane.rootPane.glassPane)
    highlighter.bounds = dropArea
  }

  private fun findTopDecorator(component: Component): InternalDecoratorImpl? {
    return UIUtil.uiParents(component, false).lastOrNull { it is InternalDecoratorImpl } as? InternalDecoratorImpl
  }

  private fun InternalDecoratorImpl.isSingleContentLayout(): Boolean {
    return contentManager.contents.singleOrNull().let { it != null && it !is SingleContentLayout.SubContent }
  }

  private fun getEditorSupport(sourceDecorator: InternalDecoratorImpl?): ToolWindowInEditorSupport? {
    return if (sourceDecorator != null) {
      ToolWindowContentUi.getToolWindowInEditorSupport(sourceDecorator.toolWindow)
    }
    else null
  }

  private fun canReorderTabs(decorator: InternalDecoratorImpl): Boolean {
    return AppMode.isMonolith()
           && Registry.`is`("ide.allow.tool.window.tabs.reorder", false)
           && (Registry.`is`("ide.allow.tool.window.tabs.reorder.vcs", true)
               || decorator.toolWindow.id !in VCS_TOOLWINDOW_IDS)
  }

  private sealed interface DropLocation {
    data class ToolWindow(val decorator: InternalDecoratorImpl) : DropLocation
    data class Editor(val window: EditorWindow) : DropLocation
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

      if (WindowRoundedCornersManager.isAvailable() && InternalUICustomization.getInstance()?.isRoundedTabDuringDrag == true) {
        WindowRoundedCornersManager.setRoundedCorners(this, PopupCornerType.RoundedWindow)
      }
    }
  }

  companion object {
    private val VCS_TOOLWINDOW_IDS = listOf(
      "Commit",
      "Version Control",
      "Pull Requests",
      "Merge Requests",
    )
  }
}
