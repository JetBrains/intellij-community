// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ui.docking.impl

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorComposite
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.BusyObject
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.executeOnCancelInEdt
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ScreenUtil
import com.intellij.ui.awt.DevicePoint
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.docking.*
import com.intellij.ui.docking.DockContainer.ContentResponse
import com.intellij.util.IconUtil
import com.intellij.util.containers.sequenceOfNotNull
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import java.awt.Component
import java.awt.Graphics
import java.awt.Image
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.util.function.Predicate
import javax.swing.*

@ApiStatus.Internal
@State(name = "DockManager", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)], getStateRequiresEdt = true)
class DockManagerImpl(@JvmField internal val project: Project, private val coroutineScope: CoroutineScope)
  : DockManager(), PersistentStateComponent<Element> {
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
    @Deprecated("Prefer using FileEditorManagerKeys.SHOW_NORTH_PANEL",
                replaceWith = ReplaceWith("FileEditorManagerKeys.SHOW_NORTH_PANEL",
                                          imports = ["com.intellij.openapi.fileEditor.FileEditorManagerKeys"]))
    val SHOW_NORTH_PANEL: Key<Boolean> = FileEditorManagerKeys.SHOW_NORTH_PANEL

    @Deprecated("Prefer using FileEditorManagerKeys.WINDOW_DIMENSION_KEY",
                replaceWith = ReplaceWith("FileEditorManagerKeys.WINDOW_DIMENSION_KEY",
                                          imports = ["com.intellij.openapi.fileEditor.FileEditorManagerKeys"]))
    val WINDOW_DIMENSION_KEY: Key<String> = FileEditorManagerKeys.WINDOW_DIMENSION_KEY

    @JvmField
    @ApiStatus.ScheduledForRemoval
    @Deprecated("Prefer using FileEditorManagerKeys.REOPEN_WINDOW",
                replaceWith = ReplaceWith("FileEditorManagerKeys.REOPEN_WINDOW",
                                          imports = ["com.intellij.openapi.fileEditor.FileEditorManagerKeys"]))
    val REOPEN_WINDOW: Key<Boolean> = FileEditorManagerKeys.REOPEN_WINDOW

    private fun getWindowDimensionKey(content: DockableContent<*>): String? {
      return if (content is DockableEditor) getWindowDimensionKey(content.file) else null
    }

    private fun getWindowDimensionKey(file: VirtualFile): String? = FileEditorManagerKeys.WINDOW_DIMENSION_KEY.get(file)

    @JvmStatic
    fun isNorthPanelVisible(uiSettings: UISettings): Boolean = uiSettings.showNavigationBar && !uiSettings.presentationMode

    @JvmStatic
    fun isNorthPanelAvailable(editors: List<FileEditor>): Boolean {
      for (editor in editors) {
        val value = FileEditorManagerKeys.SHOW_NORTH_PANEL.get(editor)
        if (value != null) return value
      }
      return true
    }
  }

  internal fun removeContainer(container: DockContainer) {
    containers.remove(container)
  }

  override fun register(container: DockContainer, parentDisposable: Disposable) {
    containers.add(container)
    Disposer.register(parentDisposable) { containers.remove(container) }
  }

  fun register(container: DockContainer, coroutineScope: CoroutineScope) {
    containers.add(container)
    executeOnCancelInEdt(coroutineScope) { containers.remove(container) }
  }

  override fun register(id: String, factory: DockContainerFactory, parentDisposable: Disposable) {
    factories.put(id, factory)
    if (parentDisposable !== project) {
      Disposer.register(parentDisposable) { factories.remove(id) }
    }
    readStateFor(id, true)
  }

  fun readState(requestFocus: Boolean) {
    for (id in factories.keys) {
      readStateFor(id, requestFocus)
    }
  }

  override fun getContainers(): Set<DockContainer> = getAllContainers().toSet()

  override fun getIdeFrame(container: DockContainer): IdeFrame? {
    return ComponentUtil.findUltimateParent(container.containerComponent) as? IdeFrame
  }

  override fun getDimensionKeyForFocus(key: String): String {
    val owner = IdeFocusManager.getInstance(project).focusOwner ?: return key
    val window = containerToWindow.get(getContainerFor(owner) { _ -> true })
    return if (window == null) key else "$key#${window.id}"
  }

  @Contract("null, _ -> null")
  override fun getContainerFor(c: Component?, filter: Predicate<in DockContainer>): DockContainer? {
    if (c == null) {
      return null
    }

    for (eachContainer in getAllContainers()) {
      if (SwingUtilities.isDescendingFrom(c, eachContainer.containerComponent) && filter.test(eachContainer)) {
        return eachContainer
      }
    }
    val parent = ComponentUtil.findUltimateParent(c)
    for (eachContainer in getAllContainers()) {
      if (parent === ComponentUtil.findUltimateParent(eachContainer.containerComponent) && filter.test(eachContainer)) {
        return eachContainer
      }
    }
    return null
  }

  override fun createDragSession(mouseEvent: MouseEvent, content: DockableContent<*>): DragSession {
    stopCurrentDragSession()
    for (container in getAllContainers()) {
      if (container.isEmpty && container.isDisposeWhenEmpty) {
        containerToWindow.get(container)?.setTransparent(true)
      }
    }
    return MyDragSession(mouseEvent, content).also { currentDragSession = it }
  }

  fun stopCurrentDragSession() {
    if (currentDragSession == null) {
      return
    }

    currentDragSession!!.cancelSession()
    currentDragSession = null
    busyObject.onReady()
    for (container in getAllContainers()) {
      if (!container.isEmpty) {
        containerToWindow.get(container)?.setTransparent(false)
      }
    }
  }

  internal val ready: ActionCallback
    get() = busyObject.getReady(this)

  private inner class MyDragSession(mouseEvent: MouseEvent, content: DockableContent<*>) : DragSession {
    private val window: JDialog = JDialog(ComponentUtil.getWindow(mouseEvent.component))
    private var dragImage: Image?
    private val defaultDragImage: Image
    private val content: DockableContent<*>
    val startDragContainer: DockContainer?
    private var currentOverContainer: DockContainer? = null
    private val imageContainer: JLabel

    init {
      window.isUndecorated = true
      this.content = content
      startDragContainer = getContainerFor(mouseEvent.component) { true }
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
          StartupUiUtil.drawImage(g, defaultDragImage, x, y, sourceBounds = null, op = null, observer = window)
        }
      })
      window.contentPane = imageContainer
      window.pack()
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
      for (each in getAllContainers()) {
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
        val currentOverContainer = currentOverContainer
        if (currentOverContainer == null) {
          // This relative point might be relative to a component on a different screen, with a different DPI scaling factor than
          // the target location. Ideally, we should pass the DevicePoint to createNewDockContainerFor, but that will change the API. We'll
          // fix it up inside createNewDockContainerFor
          val point = RelativePoint(e)
          if (content is DockableContentContainer) {
            content.add(point)
          }
          else {
            createNewDockContainerFor(content, point)
          }
          e.consume() //Marker for DragHelper: drag into a separate window is not tabs reordering
        }
        else {
          val point = devicePoint.toRelativePoint(currentOverContainer.containerComponent)
          currentOverContainer.add(content, point)
          // marker for DragHelper, not 'refined' drop in tab-set shouldn't affect ABC-order setting
          if (currentOverContainer is DockableEditorTabbedContainer && currentOverContainer.currentDropSide == SwingConstants.CENTER) {
            e.consume()
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
    val containers = getAllContainers().toMutableList()
    getFileManagerContainer()?.let(containers::add)

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

  private fun getFactory(type: String): DockContainerFactory {
    return requireNotNull(factories.get(type)) {
      "No factory for content type=$type"
    }
  }

  fun createNewDockContainerFor(content: DockableContent<*>, point: RelativePoint) {
    val container = getFactory(content.dockContainerType).createContainer(content)

    val file: VirtualFile? = (content as? DockableEditor)?.file
    val canReopenWindow = FileEditorManagerKeys.REOPEN_WINDOW.get(file, true)
    val window = createWindowFor(dimensionKey = getWindowDimensionKey(content = content),
                                 id = null,
                                 container = container,
                                 canReopenWindow = canReopenWindow)

    val isSingletonEditorInWindow = (content as? DockableEditor)?.isSingletonEditorInWindow ?: false
    if (!isSingletonEditorInWindow) {
      window.setupToolWindowPane()
    }
    val isNorthPanelAvailable = (content as? DockableEditor)?.isNorthPanelAvailable ?: true
    if (isNorthPanelAvailable) {
      window.setupNorthPanel()
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

  internal fun createNewDockContainerFor(
    file: VirtualFile,
    fileEditorManager: FileEditorManagerImpl,
    isSingletonEditorInWindow: Boolean,
    openFile: (EditorWindow) -> FileEditorComposite,
  ): FileEditorComposite {
    val container = createEditorDockContainer(
      fileEditorManager = fileEditorManager,
      loadingState = false,
      coroutineScope = coroutineScope.childScope(),
      isSingletonEditorInWindow = isSingletonEditorInWindow,
    )

    // Order is important here.
    // Create the dock window, then create the editor window.
    // That way, any listeners can check to see if the parent window is floating.
    val window = createWindowFor(dimensionKey = getWindowDimensionKey(file = file),
                                 id = null,
                                 container = container,
                                 canReopenWindow = FileEditorManagerKeys.REOPEN_WINDOW.get(file, true))
    if (!ApplicationManager.getApplication().isHeadlessEnvironment && !ApplicationManager.getApplication().isUnitTestMode) {
      window.show(true)
    }

    val editorWindow = container.splitters.getOrCreateCurrentWindow(file)
    val result = openFile(editorWindow)

    if (!isSingletonEditorInWindow) {
      window.setupToolWindowPane()
    }
    val isNorthPanelAvailable = isNorthPanelAvailable(result.allEditors)
    if (isNorthPanelAvailable) {
      window.setupNorthPanel()
    }

    container.add(
      DockableEditor(
        img = null,
        file = file,
        presentation = Presentation(file.name),
        preferredSize = editorWindow.size,
        isPinned = editorWindow.isFilePinned(file = file),
        isSingletonEditorInWindow = isSingletonEditorInWindow,
        isNorthPanelAvailable = isNorthPanelAvailable,
      ),
      null
    )
    SwingUtilities.invokeLater { window.uiContainer.preferredSize = null }
    return result
  }

  private fun createWindowFor(dimensionKey: String?, id: String?, container: DockContainer, canReopenWindow: Boolean): DockWindow {
    val windowId = id ?: (windowIdCounter++).toString()
    val coroutineScope = coroutineScope.childScope("DockWindow #$windowId")
    val window = DockWindow(dockManager = this,
                            coroutineScope = coroutineScope,
                            dimensionKey = dimensionKey,
                            id = windowId,
                            container = container,
                            isDialog = container is DockContainer.Dialog,
                            supportReopen = canReopenWindow)
    containerToWindow.put(container, window)
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      containerToWindow.remove(container)
      if (container is Disposable) {
        Disposer.dispose(container)
      }
    }
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

  override fun getState(): Element {
    val root = Element("state")
    for (container in getAllContainers()) {
      val window = containerToWindow.get(container)
      if (window != null && window.supportReopen && container is DockContainer.Persistent) {
        val eachWindowElement = Element("window")
        eachWindowElement.setAttribute("id", window.id)
        eachWindowElement.setAttribute("withNorthPanel", window.northPanelAvailable.toString())
        eachWindowElement.setAttribute("withToolWindowPane", (window.toolWindowPane != null).toString())
        val content = Element("content")
        content.setAttribute("type", container.dockContainerType)
        content.addContent(container.state)
        eachWindowElement.addContent(content)
        root.addContent(eachWindowElement)
      }
    }
    return root
  }

  private fun getAllContainers(): Sequence<DockContainer> {
    return sequenceOfNotNull(getFileManagerContainer()) + containers.asSequence() + containerToWindow.keys
  }

  private fun getFileManagerContainer(): DockContainer? {
    return if (project.isDefault) null else FileEditorManagerEx.getInstanceEx(project).dockContainer
  }

  override fun loadState(state: Element) {
    loadedState = state
  }

  private fun readStateFor(type: String, requestFocus: Boolean) {
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
        val frame = window.getFrame()
        if (!frame.isVisible) {
          (container as? DockableEditorTabbedContainer)?.focusOnShowing = requestFocus
          frame.isAutoRequestFocus = requestFocus
          try {
            window.show()
          }
          finally {
            frame.isAutoRequestFocus = true
          }
        }
      }
    }
  }
}
