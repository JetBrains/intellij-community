// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs

import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.executeOnCancelInEdt
import com.intellij.ui.ExperimentalUI
import kotlinx.coroutines.CoroutineScope
import java.awt.BorderLayout
import java.awt.Frame
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.SwingUtilities

private const val WINDOW_TABS_CONTAINER = "WINDOW_TABS_CONTAINER_KEY"
private const val CLOSE_MARKER = "TABS_CLOSE_MARKER"

/**
 * Shows all IDE frames as one group of project tabs. Each frame has a tab bar with one tab for each frame of the group.
 * Only the frame of the current tab is visible. All frames of the group have the same bounds and the same extended state.
 */
internal object WindowTabsManager : ProjectTabsHandler {
  private val model = WindowTabsModel<WindowEntry>()
  private var normalBounds: Rectangle? = null

  /**
   * Is `true` when the `ide.project.with.tabs.enabled` registry key is on.
   * The value does not change until the IDE restarts.
   */
  val enabled = ExperimentalUI.isNewUI() && Registry.`is`("ide.project.with.tabs.enabled", false)

  fun createAndInstallHandlerComponent(rootPane: JRootPane): JComponent {
    val tabsContainer = object : JPanel(BorderLayout()) {
      override fun getComponentGraphics(g: Graphics): Graphics {
        return InternalUICustomization.runGlobalCGTransformWithInactiveFrameSupport(this, super.getComponentGraphics(g))
      }
    }

    InternalUICustomization.getInstance()?.registerWindowBackgroundComponent(tabsContainer)

    tabsContainer.isVisible = false

    rootPane.putClientProperty(WINDOW_TABS_CONTAINER, tabsContainer)
    return tabsContainer
  }

  /**
   * Adds [frame] to the list of frames. [ProjectTabsManager] calls it when it creates the handler for the frame.
   * The frame gets a tab in the group only when it gets its project in [setProject].
   * The cancellation of [coroutineScope] removes the frame and its tab.
   */
  fun addFrame(frame: IdeFrameImpl, coroutineScope: CoroutineScope) {
    val entry = WindowEntry(frame, Disposer.newDisposable("WindowTabsEntry"))
    model.add(entry)

    installListeners(entry)
    if (frame.isVisible) {
      frameShown(frame)
    }

    executeOnCancelInEdt(coroutineScope) {
      removeFrame(entry)
    }
  }

  override fun setProject(frame: IdeFrameImpl) {
    val entry = findEntry(frame) ?: return
    if (!model.join(entry)) {
      return
    }

    val frames = getGroupFrames()
    if (frames.size == 2) {
      for (each in frames) {
        createTabBarsForFrame(each, frames)
      }
    }
    else if (frames.size > 2) {
      val index = frames.indexOf(frame)
      for (each in frames) {
        if (each === frame) {
          createTabBarsForFrame(each, frames)
        }
        else {
          insertTabForFrame(each, frame, index)
        }
      }
    }
  }

  override fun appClosing() {
    val frames = getGroupFrames()
    if (frames.size < 2) {
      return
    }

    for (frame in frames) {
      val rootPane = frame.rootPane
      if (rootPane.getClientProperty(CLOSE_MARKER) != null) {
        return
      }
      rootPane.putClientProperty(CLOSE_MARKER, true)
    }

    val manager = RecentProjectsManagerBase.getInstanceEx()
    for (frame in frames) {
      frame.frameHelper?.project?.let { manager.markPathRecent(it) }
    }
  }

  fun isTabbedWindow(frame: JFrame): Boolean {
    val entry = findEntry(frame) ?: return false
    return model.isTabbed(entry)
  }

  fun switchFrameIfPossible(frame: JFrame, next: Boolean) {
    val entry = findEntry(frame) ?: return
    model.getAdjacentGroupItem(entry, next)?.let { selectTab(it.frame) }
  }

  private val currentWindow: IdeFrameImpl?
    get() = model.current?.frame

  private fun getGroupFrames(): Array<IdeFrameImpl> = model.getGroupItems().map { it.frame }.toTypedArray()

  private fun findEntry(frame: JFrame): WindowEntry? = model.getItems().firstOrNull { it.frame === frame }

  private fun installListeners(entry: WindowEntry) {
    val frame = entry.frame

    val componentListener = object : ComponentAdapter() {
      override fun componentShown(e: ComponentEvent) = frameShown(frame)

      override fun componentMoved(e: ComponentEvent) = syncBounds(frame)

      override fun componentResized(e: ComponentEvent) = syncBounds(frame)
    }
    frame.addComponentListener(componentListener)

    val stateListener = object : WindowAdapter() {
      override fun windowStateChanged(e: WindowEvent) = syncState(frame, e.newState)
    }
    frame.addWindowStateListener(stateListener)

    Disposer.register(entry.disposable) {
      frame.removeComponentListener(componentListener)
      frame.removeWindowStateListener(stateListener)
    }
  }

  /**
   * Makes [frame] the current frame when other code shows it, for example, for a new project.
   */
  private fun frameShown(frame: IdeFrameImpl) {
    val entry = findEntry(frame) ?: return
    val current = currentWindow
    if (!model.select(entry)) {
      return
    }

    if (current != null && current.isVisible) {
      copyBoundsAndState(current, frame)
      current.isVisible = false
    }
  }

  private fun syncBounds(source: IdeFrameImpl) {
    if (source !== currentWindow || !source.isVisible || source.extendedState != Frame.NORMAL) {
      return
    }
    val bounds = source.bounds
    normalBounds = bounds
    for (entry in model.getItems()) {
      if (entry.frame !== source) {
        entry.frame.bounds = bounds
      }
    }
  }

  private fun syncState(source: IdeFrameImpl, state: Int) {
    if (source !== currentWindow || (state and Frame.ICONIFIED) != 0) {
      return
    }
    for (entry in model.getItems()) {
      if (entry.frame !== source) {
        entry.frame.extendedState = state
      }
    }
  }

  /**
   * Makes [target] the current frame and shows it. Then hides the previous current frame.
   * [target] gets the bounds and the extended state of the previous current frame, but not the minimized state.
   * Does nothing when [target] is already the current frame.
   */
  fun selectTab(target: IdeFrameImpl) {
    val entry = findEntry(target) ?: return
    val current = currentWindow
    if (!model.select(entry)) {
      return
    }

    if (current != null) {
      copyBoundsAndState(current, target)
    }
    target.isVisible = true
    target.doLayout()
    target.revalidate()
    target.repaint()
    current?.isVisible = false
  }

  private fun copyBoundsAndState(source: IdeFrameImpl, target: IdeFrameImpl) {
    val state = source.extendedState and Frame.ICONIFIED.inv()
    target.bounds = if (state == Frame.NORMAL) source.bounds else normalBounds ?: source.bounds
    target.extendedState = state
  }

  private fun removeFrame(entry: WindowEntry) {
    if (!model.contains(entry)) {
      return
    }
    val next = model.remove(entry)

    val frame = entry.frame
    val untabbedFrames = ArrayList<IdeFrameImpl>()
    if (removeFromFrame(frame, null)) {
      untabbedFrames.add(frame)
    }
    for (each in model.getItems()) {
      if (removeFromFrame(each.frame, frame)) {
        untabbedFrames.add(each.frame)
      }
    }
    Disposer.dispose(entry.disposable)

    if (next != null && !ApplicationManager.getApplication().isExitInProgress) {
      copyBoundsAndState(frame, next.frame)
      selectTab(next.frame)
    }

    // Call it after selectTab, so that the next frame shows without the DWM transition.
    for (each in untabbedFrames) {
      setTabbingPresentation(each, false)
    }
  }

  private fun moveFrameToIndex(frame: IdeFrameImpl, index: Int) {
    val entry = findEntry(frame) ?: return
    model.moveToGroupIndex(entry, index)
  }

  private fun closeFrame(frame: IdeFrameImpl) {
    frame.rootPane.putClientProperty(ProjectTabsComponent.CLOSE_TAB_KEY, true)
    SwingUtilities.invokeLater {
      frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_CLOSING))
    }
  }

  private fun createTabBarsForFrame(frame: IdeFrameImpl, tabFrames: Array<IdeFrameImpl>) {
    val entry = findEntry(frame) ?: return
    val tabs = WindowGroupTabsComponent(frame, Disposer.newDisposable(entry.disposable, "WindowTabsComponent"))

    val parentComponent = getTabsContainer(frame)
    parentComponent.add(tabs)
    tabs.createTabsForFrame(tabFrames)

    val oldVisible = parentComponent.isVisible
    parentComponent.isVisible = true

    val parent = parentComponent.parent
    if (parent == null || oldVisible) {
      return
    }
    parent.doLayout()
    parent.revalidate()
    parent.repaint()

    InternalUICustomization.getInstance()?.createProjectTab(frame, tabs)
    setTabbingPresentation(frame, true)
  }

  private fun insertTabForFrame(frame: IdeFrameImpl, tab: IdeFrameImpl, index: Int) {
    checkNotNull(getTabsComponent(getTabsContainer(frame))).insertTabForFrame(tab, index)
  }

  private fun setTabbingPresentation(window: IdeFrameImpl, on: Boolean) {
    // TODO: call native
  }

  /**
   * Removes the tab of [tab] from [frame], or all tabs when [tab] is `null`.
   * Returns `true` when [frame] loses its tab bar.
   */
  private fun removeFromFrame(frame: IdeFrameImpl, tab: IdeFrameImpl?): Boolean {
    val parentComponent = getTabsContainer(frame)
    val tabs = getTabsComponent(parentComponent) ?: return false

    if (tab != null && !tabs.removeTabFromFrame(tab)) {
      return false
    }

    parentComponent.remove(tabs)
    parentComponent.isVisible = false
    tabs.selfDispose()

    val parent = parentComponent.parent
    if (parent != null) {
      parent.doLayout()
      parent.revalidate()
      parent.repaint()
    }
    return true
  }

  private fun getTabsContainer(frame: IdeFrameImpl): JPanel = frame.rootPane.getClientProperty(WINDOW_TABS_CONTAINER) as JPanel

  private fun getTabsComponent(parentComponent: JPanel): WindowGroupTabsComponent? {
    return if (parentComponent.componentCount == 1) parentComponent.getComponent(0) as WindowGroupTabsComponent else null
  }

  private class WindowEntry(val frame: IdeFrameImpl, val disposable: Disposable)

  /**
   * Implements the project tabs with the group of [WindowTabsManager].
   */
  private class WindowGroupTabsComponent(ownerFrame: IdeFrameImpl, parentDisposable: Disposable) :
    ProjectTabsComponent(ownerFrame, ownerFrame.frameHelper?.project, parentDisposable) {
    override fun selectTab(tabFrame: IdeFrameImpl) {
      WindowTabsManager.selectTab(tabFrame)
    }

    override fun performCloseTab(tabFrame: IdeFrameImpl, closeOthers: Boolean) {
      if (closeOthers) {
        for (frame in getGroupFrames()) {
          if (frame !== tabFrame) {
            closeFrame(frame)
          }
        }
      }
      else {
        closeFrame(tabFrame)
      }
    }

    override fun moveTabToNewWindow(tabFrame: IdeFrameImpl) {
      // The group contains all frames. The tab stays in the group, and the tab label shows again.
      for (i in 0 until getTabCount()) {
        val info = getTabAt(i)
        if (info.`object` === tabFrame) {
          info.isHidden = false
        }
      }
    }

    override fun moveTabToIndex(tabFrame: IdeFrameImpl, index: Int) {
      moveFrameToIndex(tabFrame, index)
    }

    override fun insertTab(tabFrame: IdeFrameImpl, index: Int) {
      // The group contains all frames. A tab from another group is not possible.
    }

    override fun isSelectedInTabGroup(): Boolean = currentWindow === ownerFrame

    override fun findTabsComponent(frame: IdeFrameImpl): ProjectTabsComponent? = getTabsComponent(getTabsContainer(frame))
  }
}
