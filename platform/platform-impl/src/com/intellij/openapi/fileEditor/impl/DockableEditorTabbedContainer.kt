// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.actions.DragEditorTabsFusEventFields
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl.Companion.recordActionInvoked
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup
import com.intellij.internal.statistic.eventLog.events.ObjectEventData
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.fileEditor.impl.EditorTabbedContainer.DockableEditor
import com.intellij.openapi.fileEditor.impl.EditorWindow.Companion.DRAG_START_INDEX_KEY
import com.intellij.openapi.fileEditor.impl.EditorWindow.Companion.DRAG_START_LOCATION_HASH_KEY
import com.intellij.openapi.fileEditor.impl.EditorWindow.Companion.DRAG_START_PINNED_KEY
import com.intellij.openapi.options.advanced.AdvancedSettings.Companion.getBoolean
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.ui.docking.DockContainer
import com.intellij.ui.docking.DockContainer.ContentResponse
import com.intellij.ui.docking.DockableContent
import com.intellij.ui.tabs.*
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.ui.tabs.impl.TabLayout
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.update.Activatable
import kotlinx.coroutines.*
import org.intellij.lang.annotations.MagicConstant
import org.jdom.Element
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Image
import java.awt.Shape
import java.awt.event.MouseEvent
import java.awt.geom.Rectangle2D
import java.util.concurrent.CopyOnWriteArraySet
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JSplitPane
import javax.swing.SwingConstants

class DockableEditorTabbedContainer internal constructor(
  internal val splitters: EditorsSplitters,
  private val disposeWhenEmpty: Boolean,
  private val coroutineScope: CoroutineScope,
) : DockContainer.Persistent, Activatable, Disposable {
  private val listeners = CopyOnWriteArraySet<DockContainer.Listener>()
  private var currentOver: JBTabs? = null
  private var currentOverImg: Image? = null
  private var currentOverInfo: TabInfo? = null
  private var currentPainter: AbstractPainter? = null
  private var glassPaneListenerDisposable: DisposableHandle? = null
  private var wasEverShown = false
  internal var focusOnShowing = true

  override fun dispose() {
    coroutineScope.cancel()
  }

  override fun getDockContainerType(): String = DockableEditorContainerFactory.TYPE

  override fun getState(): Element {
    val editors = Element("state")
    splitters.writeExternal(editors)
    return editors
  }

  fun fireContentClosed(file: VirtualFile) {
    for (each in listeners) {
      each.contentRemoved(file)
    }
  }

  fun fireContentOpen(file: VirtualFile) {
    for (each in listeners) {
      each.contentAdded(file)
    }
  }

  override fun getAcceptArea(): RelativeRectangle = RelativeRectangle(splitters)

  override fun getContentResponse(content: DockableContent<*>, point: RelativePoint): ContentResponse {
    val tabs = getTabsAt(content, point)
    return if (tabs != null && !tabs.presentation.isHideTabs) ContentResponse.ACCEPT_MOVE else ContentResponse.DENY
  }

  private fun getTabsAt(content: DockableContent<*>, point: RelativePoint): JBTabs? {
    if (content !is DockableEditor) {
      return null
    }
    val targetTabs = splitters.getTabsAt(point)
    if (targetTabs != null) {
      return targetTabs
    }
    else {
      val window = splitters.currentWindow
      if (window != null) {
        return window.tabbedPane.tabs
      }
      else {
        for (each in splitters.getWindows()) {
          each.tabbedPane.tabs
          return each.tabbedPane.tabs
        }
      }
    }
    return null
  }

  override fun add(content: DockableContent<*>, dropTarget: RelativePoint?) {
    var window: EditorWindow? = null
    val dockableEditor = content as DockableEditor
    val file = dockableEditor.file
    val dragStartLocation = file.getUserData(DRAG_START_LOCATION_HASH_KEY)
    val sameWindow = currentOver != null && dragStartLocation != null && dragStartLocation == System.identityHashCode(currentOver)
    val dropSide = currentDropSide
    if (currentOver != null) {
      val provider = currentOver!!.dataProvider
      if (provider != null) {
        window = EditorWindow.DATA_KEY.getData(provider)
      }
      if (window != null && dropSide != -1 && dropSide != SwingConstants.CENTER) {
        window.split(
          orientation = if (dropSide == SwingConstants.BOTTOM || dropSide == SwingConstants.TOP) JSplitPane.VERTICAL_SPLIT else JSplitPane.HORIZONTAL_SPLIT,
          forceSplit = true,
          virtualFile = file,
          focusNew = true,
          fileIsSecondaryComponent = dropSide != SwingConstants.LEFT && dropSide != SwingConstants.TOP,
        )
        recordDragStats(dropSide = dropSide, sameWindow = false)
        return
      }
    }
    var dropIntoNewlyCreatedWindow = false
    if (window == null || window.isDisposed) {
      dropIntoNewlyCreatedWindow = true
      // drag outside
      window = splitters.getOrCreateCurrentWindow(file)
    }
    var dropInBetweenPinnedTabs: Boolean? = null
    var dropInPinnedRow = false
    val index = if (currentOver != null) (currentOver as JBTabsEx).dropInfoIndex else -1
    if (currentOver != null && getBoolean("editor.keep.pinned.tabs.on.left")) {
      if (index >= 0 && index <= currentOver!!.tabCount) {
        val tabInfo = if (index == currentOver!!.tabCount) null else currentOver!!.getTabAt(index)
        val previousInfo = if (index > 0) currentOver!!.getTabAt(index - 1) else null
        val previousIsPinned = previousInfo != null && previousInfo.isPinned
        dropInBetweenPinnedTabs = if (file.getUserData(DRAG_START_PINNED_KEY) == true) {
          index == 0 || tabInfo != null && tabInfo.isPinned || previousIsPinned
        }
        else {
          tabInfo?.isPinned
        }
        if (index > 0 && previousIsPinned) {
          val previousLabel = currentOver!!.getTabLabel(previousInfo)
          val bounds = previousLabel.bounds
          val dropPoint = dropTarget!!.getPoint(previousLabel)
          dropInPinnedRow = (currentOver is JBTabsImpl &&
                             TabLayout.showPinnedTabsSeparately() &&
                             (currentOver as JBTabsImpl).tabsPosition == JBTabsPosition.top) &&
                            bounds.y < dropPoint.y && bounds.maxY > dropPoint.y
        }
      }

      val dragStartIndex = file.getUserData(DRAG_START_INDEX_KEY)
      val isDroppedToOriginalPlace = dragStartIndex != null && dragStartIndex == index && sameWindow
      if (!isDroppedToOriginalPlace) {
        file.putUserData(DRAG_START_PINNED_KEY, dropInBetweenPinnedTabs)
      }
      if (dropInPinnedRow) {
        file.putUserData(DRAG_START_INDEX_KEY, index + 1)
        file.putUserData(DRAG_START_PINNED_KEY, true)
        dropInBetweenPinnedTabs = true
      }
    }
    recordDragStats(if (dropIntoNewlyCreatedWindow) -1 else SwingConstants.CENTER, sameWindow)
    coroutineScope.launch {
      val openOptions = FileEditorOpenOptions(index = index,
                                              requestFocus = true,
                                              pin = dropInBetweenPinnedTabs ?: dockableEditor.isPinned)
      splitters.manager.checkForbidSplitAndOpenFile(window, file, openOptions)
    }
  }

  private fun recordDragStats(dropSide: Int, sameWindow: Boolean) {
    val actionId = when (dropSide) {
      -1 -> "OpenElementInNewWindow"
      SwingConstants.TOP -> "SplitVertically"
      SwingConstants.LEFT -> "SplitHorizontally"
      SwingConstants.BOTTOM -> "MoveTabDown"
      SwingConstants.RIGHT -> "MoveTabRight"
      SwingConstants.CENTER -> null
      else -> null
    }
    if (actionId != null) {
      val event = AnActionEvent.createFromInputEvent(
        MouseEvent(splitters, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), 0, 0, 0, 0, false, MouseEvent.BUTTON1),
        ActionPlaces.EDITOR_TAB,
        null,
        DataContext.EMPTY_CONTEXT
      )
      recordActionInvoked(splitters.manager.project, ActionManager.getInstance().getAction(actionId), event) { eventPairs ->
        eventPairs.add(ActionsEventLogGroup.ADDITIONAL.with(ObjectEventData(DragEditorTabsFusEventFields.SAME_WINDOW.with(sameWindow))))
      }
    }
  }

  @get:MagicConstant(intValues = [
    SwingConstants.CENTER.toLong(),
    SwingConstants.TOP.toLong(),
    SwingConstants.LEFT.toLong(),
    SwingConstants.BOTTOM.toLong(),
    SwingConstants.RIGHT.toLong(),
    -1,
  ])
  val currentDropSide: Int
    get() = if (currentOver is JBTabsEx) (currentOver as JBTabsEx).dropSide else -1

  override fun processDropOver(content: DockableContent<*>, point: RelativePoint): Image? {
    val current = getTabsAt(content, point)
    if (currentOver != null && currentOver !== current) {
      resetDropOver(content)
    }

    if (currentOver == null && current != null) {
      currentOver = current
      val presentation = content.presentation
      currentOverInfo = TabInfo(JLabel("")).setText(presentation.text).setIcon(presentation.icon)
      currentOverImg = currentOver!!.startDropOver(currentOverInfo, point)
    }

    currentOver?.processDropOver(currentOverInfo, point)
    if (currentPainter == null) {
      currentPainter = MyDropAreaPainter()
      val disposable = Disposer.newDisposable("GlassPaneListeners")
      val handle = coroutineScope.coroutineContext.job.invokeOnCompletion {
        Disposer.dispose(disposable)
      }
      glassPaneListenerDisposable = DisposableHandle {
        try {
          Disposer.dispose(disposable)
        }
        finally {
          handle.dispose()
        }
      }
      IdeGlassPaneUtil.find(currentOver!!.component).addPainter(currentOver!!.component, currentPainter!!, disposable)
    }
    (currentPainter as? MyDropAreaPainter)?.processDropOver()
    return currentOverImg
  }

  override fun resetDropOver(content: DockableContent<*>) {
    if (currentOver != null) {
      currentOver!!.resetDropOver(currentOverInfo)
      currentOver = null
      currentOverInfo = null
      currentOverImg = null
      glassPaneListenerDisposable?.dispose()
      currentPainter = null
    }
  }

  override fun getContainerComponent(): JComponent = splitters

  fun close(file: VirtualFile) {
    splitters.closeFile(file, false)
  }

  override fun closeAll() {
    for (each in splitters.openFileList) {
      close(each)
    }
  }

  override fun addListener(listener: DockContainer.Listener, parent: Disposable) {
    listeners.add(listener)
    Disposer.register(parent) { listeners.remove(listener) }
  }

  override fun isEmpty(): Boolean = splitters.isEmptyVisible

  override fun isDisposeWhenEmpty(): Boolean = disposeWhenEmpty

  override fun showNotify() {
    if (!wasEverShown) {
      wasEverShown = true
      splitters.openFilesAsync(focusOnShowing)
    }
  }

  private inner class MyDropAreaPainter : AbstractPainter() {
    private var boundingBox: Shape? = null
    override fun needsRepaint(): Boolean {
      return boundingBox != null
    }

    override fun executePaint(component: Component, g: Graphics2D) {
      if (boundingBox == null) {
        return
      }
      GraphicsUtil.setupAAPainting(g)
      g.color = JBUI.CurrentTheme.DragAndDrop.Area.BACKGROUND
      g.fill(boundingBox)
    }

    fun processDropOver() {
      boundingBox = null
      setNeedsRepaint(true)
      val r = currentOver!!.dropArea
      val currentDropSide: Int = currentDropSide
      if (currentDropSide == -1) {
        return
      }

      TabsUtil.updateBoundsWithDropSide(r, currentDropSide)
      boundingBox = Rectangle2D.Double(r.x.toDouble(), r.y.toDouble(), r.width.toDouble(), r.height.toDouble())
    }
  }

  override fun toString(): String = "DockableEditorTabbedContainer windows=${splitters.getWindows().joinToString()}"
}