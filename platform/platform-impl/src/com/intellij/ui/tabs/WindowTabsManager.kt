// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs

import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ex.IdeFrameEx
import com.intellij.openapi.wm.impl.FrameBoundsConverter
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.executeOnCancelInEdt
import com.intellij.openapi.wm.impl.getNativeNormalPosition
import com.intellij.openapi.wm.impl.setNativeNormalPosition
import com.intellij.ui.ExperimentalUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
 * A frame gets the full screen mode of the current frame when it becomes the current frame.
 */
internal object WindowTabsManager : ProjectTabsHandler {
  private val model = WindowTabsModel<WindowEntry>()

  /**
   * Is `true` when the `ide.project.with.tabs.enabled` registry key is on.
   * The value does not change until the IDE restarts.
   */
  val enabled = SystemInfoRt.isWindows && ExperimentalUI.isNewUI() && Registry.`is`("ide.project.with.tabs.enabled", false)

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
   * When the frame comes again for the next project, this removes the entry of the previous project first.
   */
  fun addFrame(frame: IdeFrameImpl, coroutineScope: CoroutineScope) {
    val tabsContainer = frame.rootPane.getClientProperty(WINDOW_TABS_CONTAINER) as? JPanel ?: return
    findEntry(frame)?.let { removeFrame(it) }

    val entry = WindowEntry(frame, tabsContainer, Disposer.newDisposable("WindowTabsEntry"))
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
      val job = copyBoundsAndState(current, frame)
      if (job == null) {
        current.isVisible = false
      }
      else {
        hideAfter(job, current, frame)
      }
    }
  }

  /**
   * Gives the normal bounds of [source] to all other frames.
   * Without the native normal position, this does nothing when [source] is not in the normal state.
   * This does nothing when [source] is in the full screen mode, because then its bounds are the bounds of the screen.
   */
  private fun syncBounds(source: IdeFrameImpl) {
    if (source !== currentWindow || !source.isVisible || source.isInFullScreen() || source.togglingFullScreenInProgress) {
      return
    }
    val position = source.getNativeNormalPosition()
    if (position == null && source.extendedState != Frame.NORMAL) {
      return
    }
    for (entry in model.getItems()) {
      if (entry.frame !== source) {
        copyNormalBounds(source, position, entry.frame)
      }
    }
  }

  /**
   * Gives the normal bounds of [source] to [target]. [position] is the native normal position of [source].
   * On Windows, a hidden frame keeps its own native normal position, also after [IdeFrameImpl.setBounds].
   * So this sets the native normal position of a hidden [target] directly.
   * When [target] is in the full screen mode, this sets only [IdeFrameImpl.normalBounds] of [target].
   * The decorator of [target] restores these bounds when [target] leaves the full screen mode.
   */
  private fun copyNormalBounds(source: IdeFrameImpl, position: IntArray?, target: IdeFrameImpl) {
    if (target.isInFullScreen()) {
      target.normalBounds = getNormalBounds(source, position)
      return
    }
    if (position != null && target.setNativeNormalPosition(position)) {
      return
    }
    target.bounds = getNormalBounds(source, position)
  }

  /**
   * Returns the bounds of [frame] in the normal state. [position] is the native normal position of [frame].
   * In the full screen mode, the bounds come from [IdeFrameImpl.normalBounds].
   * The decorator of [frame] saves them before it enters the full screen mode.
   */
  private fun getNormalBounds(frame: IdeFrameImpl, position: IntArray?): Rectangle {
    if (frame.isInFullScreen()) {
      return frame.normalBounds ?: frame.bounds
    }
    if (position != null) {
      val (left, top, right, bottom) = position
      val bounds = Rectangle(left, top, right - left, bottom - top)
      frame.graphicsConfiguration?.let { FrameBoundsConverter.scaleDown(bounds, it) }
      return bounds
    }
    return if (frame.extendedState == Frame.NORMAL) frame.bounds else frame.normalBounds ?: frame.bounds
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
   * [target] gets the bounds, the extended state and the full screen mode of the previous current frame, but not the minimized state.
   * Does nothing when [target] is already the current frame.
   */
  fun selectTab(target: IdeFrameImpl) {
    val entry = findEntry(target) ?: return
    val current = currentWindow
    if (!model.select(entry)) {
      return
    }
    showTab(target, current, current)
  }

  /**
   * Shows [target] with the bounds, the extended state and the full screen mode of [source]. Then hides [previous].
   * When the full screen mode of [target] changes, the decorator of [target] shows it. Then [previous] hides.
   */
  private fun showTab(target: IdeFrameImpl, source: IdeFrameImpl?, previous: IdeFrameImpl?) {
    val job = source?.let { copyBoundsAndState(it, target) }
    if (job != null) {
      hideAfter(job, previous, target)
      return
    }
    target.isVisible = true
    target.doLayout()
    target.revalidate()
    target.repaint()
    previous?.isVisible = false
  }

  /**
   * Hides [previous] and [target] after [job], when they are not the current frame.
   * Shows [target] when it is the current frame and the [job] did not show it.
   */
  private fun hideAfter(job: Job, previous: IdeFrameImpl?, target: IdeFrameImpl) {
    job.invokeOnCompletion {
      SwingUtilities.invokeLater {
        val current = currentWindow
        if (current === target && !target.isVisible) {
          target.isVisible = true
        }
        for (frame in listOfNotNull(previous, target)) {
          if (frame !== current && frame.isVisible) {
            frame.isVisible = false
          }
        }
      }
    }
  }

  /**
   * Gives the bounds, the extended state and the full screen mode of [source] to [target].
   * Returns the job that changes the full screen mode of [target], or `null` when the mode does not change.
   */
  private fun copyBoundsAndState(source: IdeFrameImpl, target: IdeFrameImpl): Job? {
    val state = source.extendedState and Frame.ICONIFIED.inv()
    val fullScreen = source.isInFullScreen()
    val frame = target.frameHelper?.helper as? IdeFrameEx
    if (fullScreen == target.isInFullScreen() || frame == null) {
      copyNormalBounds(source, source.getNativeNormalPosition(), target)
      target.extendedState = state
      return null
    }

    // The decorator of target uses these values when it changes the full screen mode.
    val bounds = getNormalBounds(source, source.getNativeNormalPosition())
    target.normalBounds = bounds
    if (fullScreen) {
      target.bounds = bounds
    }
    target.extendedState = state
    return frame.toggleFullScreen(fullScreen)
  }

  private fun removeFrame(entry: WindowEntry) {
    if (!model.contains(entry)) {
      return
    }
    val next = model.remove(entry)

    val frame = entry.frame
    // The frame has a different root pane when WindowManagerImpl keeps it on the screen for the next project.
    val reused = frame.rootPane.getClientProperty(WINDOW_TABS_CONTAINER) !== entry.tabsContainer

    val untabbedFrames = ArrayList<IdeFrameImpl>()
    if (removeFromFrame(entry, null)) {
      untabbedFrames.add(frame)
    }
    for (each in model.getItems()) {
      if (removeFromFrame(each, frame)) {
        untabbedFrames.add(each.frame)
      }
    }
    Disposer.dispose(entry.disposable)

    if (next != null && !reused && !ApplicationManager.getApplication().isExitInProgress && model.select(next)) {
      showTab(next.frame, frame, null)
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
    ApplicationManager.getApplication().invokeLater({
      frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_CLOSING))
    }, ModalityState.nonModal())
  }

  private fun createTabBarsForFrame(frame: IdeFrameImpl, tabFrames: Array<IdeFrameImpl>) {
    val entry = findEntry(frame) ?: return
    val tabs = WindowGroupTabsComponent(frame, Disposer.newDisposable(entry.disposable, "WindowTabsComponent"))

    val parentComponent = entry.tabsContainer
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
    val entry = findEntry(frame) ?: return
    checkNotNull(getTabsComponent(entry.tabsContainer)).insertTabForFrame(tab, index)
  }

  private fun setTabbingPresentation(window: IdeFrameImpl, on: Boolean) {
    // TODO: call native
  }

  /**
   * Removes the tab of [tab] from the frame of [entry], or all tabs when [tab] is `null`.
   * Returns `true` when the frame of [entry] loses its tab bar.
   */
  private fun removeFromFrame(entry: WindowEntry, tab: IdeFrameImpl?): Boolean {
    val parentComponent = entry.tabsContainer
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

  private fun getTabsComponent(parentComponent: JPanel): WindowGroupTabsComponent? {
    return if (parentComponent.componentCount == 1) parentComponent.getComponent(0) as WindowGroupTabsComponent else null
  }

  /**
   * [tabsContainer] is the container of the tab bar in the root pane that [frame] has when it gets the entry.
   */
  private class WindowEntry(val frame: IdeFrameImpl, val tabsContainer: JPanel, val disposable: Disposable)

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

    override fun findTabsComponent(frame: IdeFrameImpl): ProjectTabsComponent? {
      return findEntry(frame)?.let { getTabsComponent(it.tabsContainer) }
    }
  }
}
