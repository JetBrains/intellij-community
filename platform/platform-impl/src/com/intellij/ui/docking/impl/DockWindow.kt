// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ui.docking.impl

import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.impl.DockableEditorTabbedContainer
import com.intellij.openapi.ui.FrameWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.platform.util.coroutines.childScope
import com.intellij.toolWindow.ToolWindowButtonManager
import com.intellij.toolWindow.ToolWindowPane
import com.intellij.toolWindow.ToolWindowPaneNewButtonManager
import com.intellij.toolWindow.ToolWindowPaneOldButtonManager
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.VerticalBox
import com.intellij.ui.docking.DockContainer
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.CoroutineScope
import java.awt.BorderLayout
import java.awt.Window
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

internal class DockWindow(
  private val dockManager: DockManagerImpl,
  dimensionKey: String?,
  val id: String,
  private var container: DockContainer,
  isDialog: Boolean,
  val supportReopen: Boolean,
  coroutineScope: CoroutineScope,
) : FrameWrapper(project = dockManager.project,
                 dimensionKey = dimensionKey ?: "dock-window-$id",
                 isDialog = isDialog,
                 coroutineScope = coroutineScope) {
  var northPanelAvailable: Boolean = false
  private val northPanel = VerticalBox()
  private val northExtensions = LinkedHashMap<String, JComponent>()
  val uiContainer: NonOpaquePanel = NonOpaquePanel(BorderLayout())
  private val centerPanel = JPanel(BorderLayout(0, 2))
  val dockContentUiContainer: JPanel
  var toolWindowPane: ToolWindowPane? = null

  override val isDockWindow: Boolean
    get() = true

  init {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment && container !is DockContainer.Dialog) {
      val mainStatusBar = WindowManager.getInstance().getStatusBar(dockManager.project)
      if (mainStatusBar != null) {
        val frame = getFrame()
        if (frame is IdeFrame) {
          statusBar = mainStatusBar.createChild(coroutineScope = coroutineScope.childScope(), frame = frame, editorProvider = {
            (container as? DockableEditorTabbedContainer)?.splitters?.currentWindow?.selectedComposite?.selectedWithProvider?.fileEditor
          })
        }
      }
    }
    centerPanel.isOpaque = false
    dockContentUiContainer = JPanel(BorderLayout())
    dockContentUiContainer.isOpaque = false
    dockContentUiContainer.add(container.containerComponent, BorderLayout.CENTER)
    centerPanel.add(dockContentUiContainer, BorderLayout.CENTER)
    uiContainer.add(centerPanel, BorderLayout.CENTER)
    statusBar?.let {
      uiContainer.add(it.component!!, BorderLayout.SOUTH)
    }
    component = uiContainer
    IdeEventQueue.getInstance().addPostprocessor({ e ->
                                                   if (e is KeyEvent) {
                                                     dockManager.stopCurrentDragSession()
                                                   }
                                                   false
                                                 }, coroutineScope)
    container.addListener(object : DockContainer.Listener {
      override fun contentRemoved(key: Any) {
        dockManager.ready.doWhenDone(Runnable(::closeIfEmpty))
      }
    }, this)
  }

  fun setupToolWindowPane() {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }
    val frame = getFrame() as? JFrame ?: return
    if (toolWindowPane != null) {
      return
    }

    val paneId = dimensionKey!!
    val buttonManager: ToolWindowButtonManager
    if (ExperimentalUI.isNewUI()) {
      buttonManager = ToolWindowPaneNewButtonManager(paneId, false)
      buttonManager.initMoreButton(dockManager.project)
      buttonManager.updateResizeState(null)
    }
    else {
      buttonManager = ToolWindowPaneOldButtonManager(paneId)
    }
    val containerComponent = container.containerComponent
    toolWindowPane = ToolWindowPane.create(frame = frame, coroutineScope = coroutineScope!!.childScope(), paneId = paneId,
                                           buttonManager = buttonManager)
    val toolWindowManagerImpl = ToolWindowManager.getInstance(dockManager.project) as ToolWindowManagerImpl
    toolWindowManagerImpl.addToolWindowPane(toolWindowPane!!, this)

    toolWindowPane!!.setDocumentComponent(containerComponent)
    dockContentUiContainer.remove(containerComponent)
    val toolWindowsComponent = buttonManager.wrapWithControls(toolWindowPane!!)
    dockContentUiContainer.add(toolWindowsComponent, BorderLayout.CENTER)

    // Close the container if it's empty, and we've just removed the last tool window
    dockManager.project.messageBus.connect(coroutineScope).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun stateChanged(toolWindowManager: ToolWindowManager, eventType: ToolWindowManagerListener.ToolWindowManagerEventType) {
        // Various events can mean a tool window has been removed from the frame's stripes. The comments are not exhaustive
        if (eventType == ToolWindowManagerListener.ToolWindowManagerEventType.HideToolWindow
            || eventType == ToolWindowManagerListener.ToolWindowManagerEventType.SetSideToolAndAnchor   // The last tool window dragged to another stripe on another frame
            || eventType == ToolWindowManagerListener.ToolWindowManagerEventType.SetToolWindowType      // Last tool window made floating
            || eventType == ToolWindowManagerListener.ToolWindowManagerEventType.ToolWindowUnavailable  // Last tool window programmatically set unavailable
            || eventType == ToolWindowManagerListener.ToolWindowManagerEventType.UnregisterToolWindow) {
          dockManager.ready.doWhenDone(Runnable(::closeIfEmpty))
        }
      }
    })
  }

  fun replaceContainer(container: DockContainer): DockContainer {
    val newContainerComponent = container.containerComponent
    if (toolWindowPane != null) {
      toolWindowPane!!.setDocumentComponent(newContainerComponent)
    }
    else {
      dockContentUiContainer.remove(this.container.containerComponent)
      dockContentUiContainer.add(newContainerComponent)
    }
    val oldContainer = this.container
    this.container = container
    if (container is Activatable && getFrame().isVisible) {
      (container as Activatable).showNotify()
    }
    return oldContainer
  }

  private fun closeIfEmpty() {
    if (container.isEmpty && (toolWindowPane == null || !toolWindowPane!!.buttonManager.hasButtons())) {
      close()
      dockManager.removeContainer(container)
    }
  }

  fun setupNorthPanel() {
    if (northPanelAvailable) {
      return
    }
    centerPanel.add(northPanel, BorderLayout.NORTH)
    northPanelAvailable = true
    dockManager.project.messageBus.connect(coroutineScope!!).subscribe(UISettingsListener.TOPIC, UISettingsListener { uiSettings ->
      val visible = DockManagerImpl.isNorthPanelVisible(uiSettings)
      if (northPanel.isVisible != visible) {
        updateNorthPanel(visible)
      }
    })
    updateNorthPanel(DockManagerImpl.isNorthPanelVisible(UISettings.getInstance()))
  }

  override fun getNorthExtension(key: String?): JComponent? = northExtensions.get(key)

  private fun updateNorthPanel(visible: Boolean) {
    if (ApplicationManager.getApplication().isUnitTestMode || !northPanelAvailable) {
      return
    }

    northPanel.removeAll()
    northExtensions.clear()

    northPanel.isVisible = visible && container !is DockContainer.Dialog
    for (extension in IdeRootPaneNorthExtension.EP_NAME.extensionList) {
      val component = extension.createComponent(dockManager.project, true) ?: continue
      northExtensions.put(extension.key, component)
      northPanel.add(component)
    }

    northPanel.revalidate()
    northPanel.repaint()
  }

  fun setTransparent(transparent: Boolean) {
    val windowManager = WindowManagerEx.getInstanceEx()
    if (transparent) {
      windowManager.setAlphaModeEnabled(getFrame(), true)
      windowManager.setAlphaModeRatio(getFrame(), 0.5f)
    }
    else {
      windowManager.setAlphaModeEnabled(getFrame(), true)
      windowManager.setAlphaModeRatio(getFrame(), 0f)
    }
  }

  override fun dispose() {
    super.dispose()

    if (container is Disposable) {
      Disposer.dispose((container as Disposable))
    }
    northExtensions.clear()
  }

  override fun createJFrame(parent: IdeFrame): JFrame {
    val frame = super.createJFrame(parent)
    installListeners(frame)
    return frame
  }

  override fun createJDialog(parent: IdeFrame): JDialog {
    val frame = super.createJDialog(parent)
    installListeners(frame)
    return frame
  }

  private fun installListeners(frame: Window) {
    val uiNotifyConnector = if (container is Activatable) {
      UiNotifyConnector.installOn((frame as RootPaneContainer).contentPane, (container as Activatable))
    }
    else {
      null
    }
    frame.addWindowListener(object : WindowAdapter() {
      override fun windowClosing(e: WindowEvent) {
        container.closeAll()
        if (uiNotifyConnector != null) {
          Disposer.dispose(uiNotifyConnector)
        }
      }
    })
  }
}
