// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ui.docking.impl

import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorComposite
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.*
import com.intellij.openapi.fileEditor.impl.EditorTabbedContainer.DockableEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.FrameWrapper
import com.intellij.openapi.util.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import com.intellij.toolWindow.ToolWindowButtonManager
import com.intellij.toolWindow.ToolWindowPane
import com.intellij.toolWindow.ToolWindowPaneNewButtonManager
import com.intellij.toolWindow.ToolWindowPaneOldButtonManager
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.ScreenUtil
import com.intellij.ui.awt.DevicePoint
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.VerticalBox
import com.intellij.ui.docking.*
import com.intellij.ui.docking.DockContainer.ContentResponse
import com.intellij.util.IconUtil
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.sequenceOfNotNull
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import org.jdom.Element
import org.jetbrains.annotations.Contract
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.*
import java.util.function.Predicate
import javax.swing.*

@State(name = "DockManager", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
class DockManagerImpl(private val project: Project) : DockManager(), PersistentStateComponent<Element?> {
  private val factories = HashMap<String, DockContainerFactory>()
  private val containers = HashSet<DockContainer>()
  private val containerToWindow = HashMap<DockContainer, DockWindow>()
  private var currentDragSession: MyDragSession? = null

  private val busyObject: BusyObject.Impl = object : BusyObject.Impl() {
    override fun isReady(): Boolean = currentDragSession == null
  }

  private var windowIdCounter = 1
  private var loadedState: Element? = null

  companion object {
    val SHOW_NORTH_PANEL = Key.create<Boolean>("SHOW_NORTH_PANEL")
    val WINDOW_DIMENSION_KEY = Key.create<String>("WINDOW_DIMENSION_KEY")
    @JvmField
    val REOPEN_WINDOW = Key.create<Boolean>("REOPEN_WINDOW")
    @JvmField
    val ALLOW_DOCK_TOOL_WINDOWS = Key.create<Boolean>("ALLOW_DOCK_TOOL_WINDOWS")

    @JvmStatic
    fun isSingletonEditorInWindow(editors: List<FileEditor>): Boolean {
      return editors.any { FileEditorManagerImpl.SINGLETON_EDITOR_IN_WINDOW.get(it, false) || EditorWindow.HIDE_TABS.get(it, false) }
    }

    private fun getWindowDimensionKey(content: DockableContent<*>): String? {
      return if (content is DockableEditor) getWindowDimensionKey(content.file) else null
    }

    private fun getWindowDimensionKey(file: VirtualFile): String? = WINDOW_DIMENSION_KEY.get(file)

    @JvmStatic
    fun isNorthPanelVisible(uiSettings: UISettings): Boolean {
      return uiSettings.showNavigationBar && !uiSettings.presentationMode
    }

    @JvmStatic
    fun isNorthPanelAvailable(editors: List<FileEditor>): Boolean {
      val defaultNorthPanelVisible = isNorthPanelVisible(UISettings.getInstance())
      for (editor in editors) {
        if (SHOW_NORTH_PANEL.isIn(editor)) {
          return SHOW_NORTH_PANEL.get(editor, defaultNorthPanelVisible)
        }
      }
      return defaultNorthPanelVisible
    }
  }

  override fun register(container: DockContainer, parentDisposable: Disposable) {
    containers.add(container)
    Disposer.register(parentDisposable) { containers.remove(container) }
  }

  override fun register(id: String, factory: DockContainerFactory, parentDisposable: Disposable) {
    factories.put(id, factory)
    if (parentDisposable !== project) {
      Disposer.register(parentDisposable) { factories.remove(id) }
    }
    readStateFor(id)
  }

  fun readState() {
    for (id in factories.keys) {
      readStateFor(id)
    }
  }

  override fun getContainers(): Set<DockContainer> = allContainers.toSet()

  override fun getIdeFrame(container: DockContainer): IdeFrame? {
    return ComponentUtil.findUltimateParent(container.containerComponent) as? IdeFrame
  }

  override fun getDimensionKeyForFocus(key: String): String {
    val owner = IdeFocusManager.getInstance(project).focusOwner ?: return key
    val window = containerToWindow.get(getContainerFor(owner) { _ -> true })
    return if (window == null) key else "$key#${window.id}"
  }

  @Suppress("OVERRIDE_DEPRECATION", "removal")
  override fun getContainerFor(c: Component): DockContainer? {
    return getContainerFor(c) { true }
  }

  @Contract("null, _ -> null")
  override fun getContainerFor(c: Component?, filter: Predicate<in DockContainer>): DockContainer? {
    if (c == null) {
      return null
    }

    for (eachContainer in allContainers) {
      if (SwingUtilities.isDescendingFrom(c, eachContainer.containerComponent) && filter.test(eachContainer)) {
        return eachContainer
      }
    }
    val parent = ComponentUtil.findUltimateParent(c)
    for (eachContainer in allContainers) {
      if (parent === ComponentUtil.findUltimateParent(eachContainer.containerComponent) && filter.test(eachContainer)) {
        return eachContainer
      }
    }
    return null
  }

  override fun createDragSession(mouseEvent: MouseEvent, content: DockableContent<*>): DragSession {
    stopCurrentDragSession()
    for (each in allContainers) {
      if (each.isEmpty && each.isDisposeWhenEmpty) {
        val window = containerToWindow.get(each)
        window?.setTransparent(true)
      }
    }
    currentDragSession = MyDragSession(mouseEvent, content)
    return currentDragSession!!
  }

  fun stopCurrentDragSession() {
    if (currentDragSession != null) {
      currentDragSession!!.cancelSession()
      currentDragSession = null
      busyObject.onReady()
      for (each in allContainers) {
        if (!each.isEmpty) {
          val window = containerToWindow.get(each)
          window?.setTransparent(false)
        }
      }
    }
  }

  private val ready: ActionCallback
    get() = busyObject.getReady(this)

  private inner class MyDragSession(mouseEvent: MouseEvent, content: DockableContent<*>) : DragSession {
    private val window: JDialog
    private var dragImage: Image?
    private val defaultDragImage: Image
    private val content: DockableContent<*>
    val startDragContainer: DockContainer?
    private var currentOverContainer: DockContainer? = null
    private val imageContainer: JLabel

    init {
      window = JDialog(ComponentUtil.getWindow(mouseEvent.component))
      window.isUndecorated = true
      this.content = content
      @Suppress("DEPRECATION")
      startDragContainer = getContainerFor(mouseEvent.component)
      val buffer = ImageUtil.toBufferedImage(content.previewImage)
      val requiredSize = 220.0
      val width = buffer.getWidth(null).toDouble()
      val height = buffer.getHeight(null).toDouble()
      val ratio = if (width > height) requiredSize / width else requiredSize / height
      defaultDragImage = buffer.getScaledInstance((width * ratio).toInt(), (height * ratio).toInt(), Image.SCALE_SMOOTH)
      dragImage = defaultDragImage
      imageContainer = JLabel(object : Icon {
        override fun getIconWidth(): Int = defaultDragImage.getWidth(window)

        override fun getIconHeight(): Int = defaultDragImage.getHeight(window)

        @Synchronized
        override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
          StartupUiUtil.drawImage(g, defaultDragImage, x, y, window)
        }
      })
      window.contentPane = imageContainer
      setLocationFrom(mouseEvent)
      window.isVisible = true
      val windowManager = WindowManagerEx.getInstanceEx()
      windowManager.setAlphaModeEnabled(window, true)
      windowManager.setAlphaModeRatio(window, 0.1f)
    }

    private fun setLocationFrom(me: MouseEvent) {
      val devicePoint = DevicePoint(me)
      val showPoint = devicePoint.locationOnScreen
      val size = imageContainer.size
      showPoint.x -= size.width / 2
      showPoint.y -= size.height / 2
      window.bounds = Rectangle(showPoint, size)
    }

    override fun getResponse(e: MouseEvent): ContentResponse {
      val point = DevicePoint(e)
      for (each in allContainers) {
        val rec = each.acceptArea
        if (rec.contains(point)) {
          val component = each.containerComponent
          if (component.graphicsConfiguration != null) {
            val response = each.getContentResponse(content, point.toRelativePoint(component))
            if (response.canAccept()) {
              return response
            }
          }
        }
      }
      return ContentResponse.DENY
    }

    override fun process(e: MouseEvent) {
      val devicePoint = DevicePoint(e)
      var img: Image? = null
      if (e.id == MouseEvent.MOUSE_DRAGGED) {
        val over = findContainerFor(devicePoint, content)
        if (currentOverContainer != null && currentOverContainer !== over) {
          currentOverContainer!!.resetDropOver(content)
          currentOverContainer = null
        }
        if (currentOverContainer == null && over != null) {
          currentOverContainer = over
          val point = devicePoint.toRelativePoint(over.containerComponent)
          img = currentOverContainer!!.startDropOver(content, point)
        }
        if (currentOverContainer != null) {
          val point = devicePoint.toRelativePoint(currentOverContainer!!.containerComponent)
          img = currentOverContainer!!.processDropOver(content, point)
        }
        if (img == null) {
          img = defaultDragImage
        }
        if (img !== dragImage) {
          dragImage = img
          imageContainer.icon = IconUtil.createImageIcon(dragImage!!)
          window.pack()
        }
        setLocationFrom(e)
      }
      else if (e.id == MouseEvent.MOUSE_RELEASED) {
        if (currentOverContainer == null) {
          // This relative point might be relative to a component that's on a different screen, with a different DPI scaling factor than
          // the target location. Ideally, we should pass the DevicePoint to createNewDockContainerFor, but that will change the API. We'll
          // fix it up inside createNewDockContainerFor
          val point = RelativePoint(e)
          createNewDockContainerFor(content, point)
          e.consume() //Marker for DragHelper: drag into separate window is not tabs reordering
        }
        else {
          val point = devicePoint.toRelativePoint(currentOverContainer!!.containerComponent)
          currentOverContainer!!.add(content, point)
          ObjectUtils.consumeIfCast(currentOverContainer,
                                    DockableEditorTabbedContainer::class.java) { container: DockableEditorTabbedContainer ->
            //Marker for DragHelper, not 'refined' drop in tab-set shouldn't affect ABC-order setting
            if (container.currentDropSide == SwingConstants.CENTER) e.consume()
          }
        }
        stopCurrentDragSession()
      }
    }

    override fun cancel() {
      stopCurrentDragSession()
    }

    fun cancelSession() {
      window.dispose()
      if (currentOverContainer != null) {
        currentOverContainer!!.resetDropOver(content)
        currentOverContainer = null
      }
    }
  }

  private fun findContainerFor(devicePoint: DevicePoint, content: DockableContent<*>): DockContainer? {
    val containers = containers.toMutableList()
    FileEditorManagerEx.getInstanceEx(project)?.dockContainer?.let(containers::add)

    val startDragContainer = currentDragSession?.startDragContainer
    if (startDragContainer != null) {
      containers.remove(startDragContainer)
      containers.add(0, startDragContainer)
    }
    for (each in containers) {
      val rec = each.acceptArea
      if (rec.contains(devicePoint) && each.getContentResponse(content, devicePoint.toRelativePoint(each.containerComponent)).canAccept()) {
        return each
      }
    }
    for (each in containers) {
      val rec = each.acceptAreaFallback
      if (rec.contains(devicePoint) && each.getContentResponse(content, devicePoint.toRelativePoint(each.containerComponent)).canAccept()) {
        return each
      }
    }
    return null
  }

  private fun getFactory(type: String): DockContainerFactory? {
    assert(factories.containsKey(type)) { "No factory for content type=$type" }
    return factories.get(type)
  }

  fun createNewDockContainerFor(content: DockableContent<*>, point: RelativePoint) {
    val container = getFactory(content.dockContainerType)!!.createContainer(content)
    val canReopenWindow = content.presentation.getClientProperty(REOPEN_WINDOW)
    val reopenWindow = canReopenWindow == null || canReopenWindow
    val window = createWindowFor(getWindowDimensionKey(content), null, container, reopenWindow)
    val isNorthPanelAvailable = if (content is DockableEditor) content.isNorthPanelAvailable else isNorthPanelVisible(UISettings.getInstance())
    if (isNorthPanelAvailable) {
      window.setupNorthPanel()
    }
    val canDockToolWindows = content.presentation.getClientProperty(ALLOW_DOCK_TOOL_WINDOWS)
    if (canDockToolWindows == null || canDockToolWindows) {
      window.setupToolWindowPane()
    }
    val size = content.preferredSize

    // The given relative point might be relative to a component on a different screen, using different DPI screen coordinates. Convert to
    // device coordinates first. Ideally, we would be given a DevicePoint
    val showPoint = DevicePoint(point).locationOnScreen
    showPoint.x -= size.width / 2
    showPoint.y -= size.height / 2
    val target = Rectangle(showPoint, size)
    ScreenUtil.moveRectangleToFitTheScreen(target)
    ScreenUtil.cropRectangleToFitTheScreen(target)
    window.setLocation(target.location)
    window.dockContentUiContainer.preferredSize = target.size
    window.show(false)
    window.getFrame().pack()
    container.add(content, RelativePoint(target.location))
    SwingUtilities.invokeLater { window.uiContainer.preferredSize = null }
  }

  fun createNewDockContainerFor(file: VirtualFile,
                                fileEditorManager: FileEditorManagerImpl): FileEditorComposite {
    val container = getFactory(DockableEditorContainerFactory.TYPE)!!.createContainer(null)

    // Order is important here. Create the dock window, then create the editor window. That way, any listeners can check to see if the
    // parent window is floating.
    val window = createWindowFor(getWindowDimensionKey(file), null, container, REOPEN_WINDOW.get(file, true))
    if (!ApplicationManager.getApplication().isHeadlessEnvironment && !ApplicationManager.getApplication().isUnitTestMode) {
      window.show(true)
    }
    val editorWindow = (container as DockableEditorTabbedContainer).splitters.getOrCreateCurrentWindow(file)
    val result = fileEditorManager.openFileImpl2(editorWindow, file, FileEditorOpenOptions(requestFocus = true))
    if (!isSingletonEditorInWindow(result.allEditors)) {
      window.setupToolWindowPane()
    }
    val isNorthPanelAvailable = isNorthPanelAvailable(result.allEditors)
    if (isNorthPanelAvailable) {
      window.setupNorthPanel()
    }
    container.add(
      EditorTabbedContainer.createDockableEditor(project, null, file, Presentation(file.name), editorWindow, isNorthPanelAvailable),
      null
    )
    SwingUtilities.invokeLater { window.uiContainer.preferredSize = null }
    return result
  }

  private fun createWindowFor(dimensionKey: String?,
                              id: String?,
                              container: DockContainer,
                              canReopenWindow: Boolean): DockWindow {
    val window = DockWindow(dimensionKey = dimensionKey,
                            id = id ?: (windowIdCounter++).toString(),
                            project = project,
                            container = container,
                            isDialog = container is DockContainer.Dialog,
                            supportReopen = canReopenWindow)
    containerToWindow.put(container, window)
    return window
  }

  private fun getOrCreateWindowFor(id: String, container: DockContainer): DockWindow {
    val existingWindow = containerToWindow.values.firstOrNull { it.id == id }
    if (existingWindow != null) {
      val oldContainer = existingWindow.replaceContainer(container)
      containerToWindow.remove(oldContainer)
      containerToWindow.put(container, existingWindow)
      if (oldContainer is Disposable) {
        Disposer.dispose(oldContainer)
      }
      return existingWindow
    }
    return createWindowFor(dimensionKey = null, id = id, container = container, canReopenWindow = true)
  }

  private inner class DockWindow(dimensionKey: String?,
                                 val id: String,
                                 project: Project,
                                 private var container: DockContainer,
                                 isDialog: Boolean,
                                 val supportReopen: Boolean) : FrameWrapper(project, dimensionKey ?: "dock-window-$id", isDialog) {
    var northPanelAvailable = false
    private val northPanel = VerticalBox()
    private val northExtensions = LinkedHashMap<String, JComponent>()
    val uiContainer: NonOpaquePanel
    private val centerPanel = JPanel(BorderLayout(0, 2))
    val dockContentUiContainer: JPanel
    var toolWindowPane: ToolWindowPane? = null

    override val isDockWindow: Boolean
      get() = true

    init {
      if (!ApplicationManager.getApplication().isHeadlessEnvironment && container !is DockContainer.Dialog) {
        val statusBar = WindowManager.getInstance().getStatusBar(project)
        if (statusBar != null) {
          val frame = getFrame()
          if (frame is IdeFrame) {
            this.statusBar = statusBar.createChild(frame)
          }
        }
      }
      uiContainer = NonOpaquePanel(BorderLayout())
      centerPanel.isOpaque = false
      dockContentUiContainer = JPanel(BorderLayout())
      dockContentUiContainer.isOpaque = false
      dockContentUiContainer.add(container.containerComponent, BorderLayout.CENTER)
      centerPanel.add(dockContentUiContainer, BorderLayout.CENTER)
      uiContainer.add(centerPanel, BorderLayout.CENTER)
      statusBar?.let {
        uiContainer.add(it.component, BorderLayout.SOUTH)
      }
      component = uiContainer
      IdeEventQueue.getInstance().addPostprocessor({ e ->
        if (e is KeyEvent) {
          if (currentDragSession != null) {
            stopCurrentDragSession()
          }
        }
        false
      }, this)
      container.addListener(object : DockContainer.Listener {
        override fun contentRemoved(key: Any) {
          ready.doWhenDone(Runnable(::closeIfEmpty))
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
        buttonManager.add(dockContentUiContainer)
        buttonManager.initMoreButton()
      }
      else {
        buttonManager = ToolWindowPaneOldButtonManager(paneId)
      }
      val containerComponent = container.containerComponent
      toolWindowPane = ToolWindowPane(frame = frame, parentDisposable = this, paneId = paneId, buttonManager = buttonManager)
      val toolWindowManagerImpl = ToolWindowManager.getInstance(project) as ToolWindowManagerImpl
      toolWindowManagerImpl.addToolWindowPane(toolWindowPane!!, this)

      toolWindowPane!!.setDocumentComponent(containerComponent)
      dockContentUiContainer.remove(containerComponent)
      dockContentUiContainer.add(toolWindowPane!!, BorderLayout.CENTER)

      // Close the container if it's empty, and we've just removed the last tool window
      project.messageBus.connect(this).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
        override fun stateChanged(toolWindowManager: ToolWindowManager, eventType: ToolWindowManagerEventType) {
          // Various events can mean a tool window has been removed from the frame's stripes. The comments are not exhaustive
          if (eventType == ToolWindowManagerEventType.HideToolWindow
            || eventType == ToolWindowManagerEventType.SetSideToolAndAnchor   // Last tool window dragged to another stripe on another frame
            || eventType == ToolWindowManagerEventType.SetToolWindowType      // Last tool window made floating
            || eventType == ToolWindowManagerEventType.ToolWindowUnavailable  // Last tool window programmatically set unavailable
            || eventType == ToolWindowManagerEventType.UnregisterToolWindow) {
            ready.doWhenDone(Runnable(::closeIfEmpty))
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
        containers.remove(container)
      }
    }

    fun setupNorthPanel() {
      if (northPanelAvailable) {
        return
      }
      centerPanel.add(northPanel, BorderLayout.NORTH)
      northPanelAvailable = true
      project.messageBus.connect(this).subscribe(UISettingsListener.TOPIC, UISettingsListener { uiSettings ->
        val visible = isNorthPanelVisible(uiSettings)
        if (northPanel.isVisible != visible) {
          updateNorthPanel(visible)
        }
      })
      updateNorthPanel(isNorthPanelVisible(UISettings.getInstance()))
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
        val component = extension.createComponent(project, true) ?: continue
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

      containerToWindow.remove(container)
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
        UiNotifyConnector((frame as RootPaneContainer).contentPane, (container as Activatable))
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

  override fun getState(): Element {
    val root = Element("state")
    for (each in allContainers) {
      val eachWindow = containerToWindow.get(each)
      if (eachWindow != null && eachWindow.supportReopen && each is DockContainer.Persistent) {
        val eachWindowElement = Element("window")
        eachWindowElement.setAttribute("id", eachWindow.id)
        eachWindowElement.setAttribute("withNorthPanel", eachWindow.northPanelAvailable.toString())
        eachWindowElement.setAttribute("withToolWindowPane", (eachWindow.toolWindowPane != null).toString())
        val content = Element("content")
        content.setAttribute("type", each.dockContainerType)
        content.addContent(each.state)
        eachWindowElement.addContent(content)
        root.addContent(eachWindowElement)
      }
    }
    return root
  }

  private val allContainers: Sequence<DockContainer>
    get() {
      return sequenceOfNotNull(FileEditorManagerEx.getInstanceEx (project)?.dockContainer) +
             containers.asSequence () +
             containerToWindow.keys
    }

  override fun loadState(state: Element) {
    loadedState = state
  }

  private fun readStateFor(type: String) {
    for (windowElement in (loadedState ?: return).getChildren("window")) {
      val eachContent = windowElement.getChild("content") ?: continue
      val eachType = eachContent.getAttributeValue("type")
      if (type != eachType || !factories.containsKey(eachType)) {
        continue
      }

      val factory = factories.get(eachType) as? DockContainerFactory.Persistent ?: continue
      val container = factory.loadContainerFrom(eachContent)

      // If the window already exists, reuse it, otherwise create it. This handles changes in tasks & contexts. When we clear the current
      // context, all open editors are closed, but a floating editor container with a docked tool window will remain open. Switching to a
      // new context will reuse the window and open editors in that editor container. If the new context doesn't use this window, or it's
      // a default clean context, the window will remain open, containing only the docked tool windows.
      val window = getOrCreateWindowFor(windowElement.getAttributeValue("id"), container)

      // If the window already exists, we can't remove the north panel or tool window pane, but we can add them
      if (windowElement.getAttributeValue("withNorthPanel", "true").toBoolean()) {
        window.setupNorthPanel()
      }
      if (windowElement.getAttributeValue("withToolWindowPane", "true").toBoolean()) {
        window.setupToolWindowPane()
      }

      // If the window exists, it's already visible. Don't show multiple times as this will set up additional listeners and window decoration
      EdtInvocationManager.invokeLaterIfNeeded {
        if (!window.getFrame().isVisible) {
          window.show()
        }
      }
    }
  }
}