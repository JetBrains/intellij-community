// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.runActivity
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.UiActivity
import com.intellij.ide.UiActivityMonitor
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.actions.MaximizeActiveDialogAction
import com.intellij.ide.ui.UISettings
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ToolWindowCollector
import com.intellij.notification.impl.NotificationsManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.MnemonicHelper
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.*
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.ui.FrameWrapper
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.commands.RequestFocusInToolWindowCmd
import com.intellij.ui.BalloonImpl
import com.intellij.ui.ComponentUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.*
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.PositionTracker
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import org.intellij.lang.annotations.JdkConstants
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeListener
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

private val LOG = Logger.getInstance(ToolWindowManagerImpl::class.java)

@State(name = "ToolWindowManager",
       defaultStateAsResource = true,
       storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE), Storage(value = StoragePathMacros.WORKSPACE_FILE, deprecated = true)]
)
open class ToolWindowManagerImpl(val project: Project) : ToolWindowManagerEx(), PersistentStateComponent<Element?> {
  private val dispatcher = EventDispatcher.create(ToolWindowManagerListener::class.java)
  private var layout = DesktopLayout()
  private val idToEntry: MutableMap<String, ToolWindowEntry> = HashMap()
  private val activeStack = ActiveStack()
  private val sideStack = SideStack()
  private var toolWindowPane: ToolWindowsPane? = null
  private var frame: ProjectFrameHelper? = null
  private var layoutToRestoreLater: DesktopLayout? = null
  private var currentState = KeyState.WAITING
  private var waiterForSecondPress: Alarm? = null

  private val pendingSetLayoutTask = AtomicReference<Runnable?>()

  private val secondPressRunnable = Runnable {
    if (currentState != KeyState.HOLD) {
      resetHoldState()
    }
  }

  fun isToolWindowRegistered(id: String) = idToEntry.containsKey(id)

  internal val commandProcessor = CommandProcessor { project.isDisposed }

  init {
    if (project.isDefault) {
      waiterForSecondPress = null
    }
    else {
      runActivity("toolwindow factory class preloading") {
        ToolWindowEP.EP_NAME.forEachExtensionSafe { bean ->
          bean.toolWindowFactory
        }
      }
    }
  }

  companion object {
    @JvmStatic
    fun getRegisteredMutableInfoOrLogError(decorator: InternalDecorator): WindowInfoImpl {
      val toolWindow = decorator.toolWindow
      return toolWindow.toolWindowManager.getRegisteredMutableInfoOrLogError(toolWindow.id)
    }
  }

  @Service
  private class ToolWindowManagerAppLevelHelper {
    private var awtFocusListener: AWTEventListener? = null

    init {
      awtFocusListener = AWTEventListener { event ->
        event as FocusEvent
        if (event.id != FocusEvent.FOCUS_GAINED) {
          return@AWTEventListener
        }

        processOpenedProjects { project ->
          val component = event.component ?: return@AWTEventListener
          for (composite in FileEditorManagerEx.getInstanceEx(project).splitters.editorComposites) {
            if (composite.editors.any { SwingUtilities.isDescendingFrom(component, it.component) }) {
              (getInstance(project) as ToolWindowManagerImpl).activeStack.clear()
            }
          }
        }
      }

      Toolkit.getDefaultToolkit().addAWTEventListener(awtFocusListener, AWTEvent.FOCUS_EVENT_MASK)

      val updateHeadersAlarm = SingleAlarm(Runnable {
        processOpenedProjects { project ->
          (getInstance(project) as ToolWindowManagerImpl).updateToolWindowHeaders()
        }
      }, 50, ApplicationManager.getApplication())
      val focusListener = PropertyChangeListener { updateHeadersAlarm.cancelAndRequest() }
      val keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
      keyboardFocusManager.addPropertyChangeListener("focusOwner", focusListener)
      Disposer.register(ApplicationManager.getApplication(), Disposable {
        keyboardFocusManager.removePropertyChangeListener("focusOwner", focusListener)
      })

      val connection = ApplicationManager.getApplication().messageBus.connect()
      connection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
        override fun projectClosed(project: Project) {
          (getInstance(project) as ToolWindowManagerImpl).projectClosed()
        }
      })

      Windows.ToolWindowProvider(Windows.Signal(Predicate { event ->
        event.id == FocusEvent.FOCUS_LOST || event.id == FocusEvent.FOCUS_GAINED || event.id == MouseEvent.MOUSE_PRESSED || event.id == KeyEvent.KEY_PRESSED
      }))
        .withEscAction()
        .handleFocusLostOnPinned { toolWindowId ->
          process { manager ->
            val entry = manager.idToEntry.get(toolWindowId) ?: return@process
            val info = manager.layout.getInfo(toolWindowId) ?: return@process
            val commands = mutableListOf<Runnable>()
            manager.deactivateToolWindowImpl(info, entry, shouldHide = true, commands = commands)
            // notify clients that toolwindow is deactivated
            manager.execute(commands)
          }
        }
        .bind(ApplicationManager.getApplication())

      connection.subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
        override fun activeKeymapChanged(keymap: Keymap?) {
          process { manager ->
            manager.idToEntry.values.forEach {
              it.stripeButton.updatePresentation()
            }
          }
        }
      })

      connection.subscribe(AnActionListener.TOPIC, object : AnActionListener {
        override fun beforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
          process { manager ->
            if (manager.currentState != KeyState.HOLD) {
              manager.resetHoldState()
            }
          }
        }
      })

      IdeEventQueue.getInstance().addDispatcher(IdeEventQueue.EventDispatcher { event ->
        if (event is KeyEvent) {
          process { manager ->
            manager.dispatchKeyEvent(event)
          }
        }

        if (event is WindowEvent && event.getID() == WindowEvent.WINDOW_LOST_FOCUS) {
          process { manager ->
            if (event.getSource() === manager.frame) {
              manager.resetHoldState()
            }
          }
        }

        false
      }, ApplicationManager.getApplication())
    }

    private inline fun process(processor: (manager: ToolWindowManagerImpl) -> Unit) {
      processOpenedProjects { project ->
        processor(getInstance(project) as ToolWindowManagerImpl)
      }
    }
  }

  private fun updateToolWindowHeaders() {
    focusManager.doWhenFocusSettlesDown(object : ExpirableRunnable.ForProject(project) {
      override fun run() {
        idToEntry.forEach { (id, entry) ->
          val info = layout.getInfo(id) ?: return@forEach
          if (info.isVisible) {
            entry.toolWindow.decorator.repaint()
          }
        }
      }
    })
  }

  fun dispatchKeyEvent(e: KeyEvent): Boolean {
    if ((e.keyCode != KeyEvent.VK_CONTROL) && (
        e.keyCode != KeyEvent.VK_ALT) && (e.keyCode != KeyEvent.VK_SHIFT) && (e.keyCode != KeyEvent.VK_META)) {
      if (e.modifiers == 0) {
        resetHoldState()
      }
      return false
    }

    if (e.id != KeyEvent.KEY_PRESSED && e.id != KeyEvent.KEY_RELEASED) {
      return false
    }

    val parent = UIUtil.findUltimateParent(e.component)
    if (parent is IdeFrame) {
      if ((parent as IdeFrame).project !== project) {
        resetHoldState()
        return false
      }
    }

    val vks = activateToolWindowVKsMask
    if (vks == 0) {
      resetHoldState()
      return false
    }

    val mouseMask = InputEvent.BUTTON1_DOWN_MASK or InputEvent.BUTTON2_DOWN_MASK or InputEvent.BUTTON3_DOWN_MASK
    if (BitUtil.isSet(vks, keyCodeToInputMask(e.keyCode)) && (e.modifiersEx and mouseMask) == 0) {
      val pressed = e.id == KeyEvent.KEY_PRESSED
      val modifiers = e.modifiers
      if (areAllModifiersPressed(modifiers, vks) || !pressed) {
        processState(pressed)
      }
      else {
        resetHoldState()
      }
    }
    return false
  }

  private fun resetHoldState() {
    currentState = KeyState.WAITING
    processHoldState()
  }

  private fun processState(pressed: Boolean) {
    if (pressed) {
      if (currentState == KeyState.WAITING) {
        currentState = KeyState.PRESSED
      }
      else if (currentState == KeyState.RELEASED) {
        currentState = KeyState.HOLD
        processHoldState()
      }
    }
    else {
      if (currentState == KeyState.PRESSED) {
        currentState = KeyState.RELEASED
        waiterForSecondPress?.let { waiterForSecondPress ->
          waiterForSecondPress.cancelAllRequests()
          waiterForSecondPress.addRequest(secondPressRunnable, SystemProperties.getIntProperty("actionSystem.keyGestureDblClickTime", 650))
        }
      }
      else {
        resetHoldState()
      }
    }
  }

  private fun processHoldState() {
    toolWindowPane?.setStripesOverlayed(currentState == KeyState.HOLD)
  }

  @TestOnly
  fun init(frameHelper: ProjectFrameHelper) {
    if (toolWindowPane != null) {
      return
    }

    // manager is used in light tests (light project is never disposed), so, earlyDisposable must be used
    val disposable = (project as ProjectEx).earlyDisposable
    waiterForSecondPress = Alarm(disposable)

    val connection = project.messageBus.connect(disposable)
    connection.subscribe(ToolWindowManagerListener.TOPIC, dispatcher.multicaster)
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        focusManager.doWhenFocusSettlesDown(object : ExpirableRunnable.ForProject(project) {
          override fun run() {
            if (FileEditorManager.getInstance(project).hasOpenFiles()) {
              focusToolWindowByDefault(null)
            }
          }
        })
      }
    })

    frame = frameHelper
    val toolWindowPane = ToolWindowsPane(frameHelper.frame, this, project)
    this.toolWindowPane = toolWindowPane

    frameHelper.rootPane!!.setToolWindowsPane(toolWindowPane)

    toolWindowPane.putClientProperty(UIUtil.NOT_IN_HIERARCHY_COMPONENTS, Iterable {
      val result = ArrayList<JComponent>(idToEntry.size)
      for (entry in idToEntry.values) {
        val component = entry.toolWindow.decoratorComponent
        if (component != null && component.parent == null) {
          result.add(component)
        }
      }
      result.iterator()
    })

    if (ApplicationManager.getApplication().isUnitTestMode) {
      commandProcessor.activate()
    }
  }

  private fun beforeProjectOpened() {
    LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread)

    val list = mutableListOf<ToolWindowEP>()
    runActivity("toolwindow init command creation") {
      ToolWindowEP.EP_NAME.forEachExtensionSafe { bean ->
        val condition = bean.condition
        // compute outside of EDT
        if (condition != null && !condition.value(project)) {
          return@forEachExtensionSafe
        }

        // compute outside of EDT (should be already preloaded, but who knows)
        val toolWindowFactory = bean.toolWindowFactory
        if (!toolWindowFactory.isApplicable(project)) {
          return@forEachExtensionSafe
        }

        list.add(bean)
      }
    }

    // must be executed in EDT
    ApplicationManager.getApplication().invokeLater(Runnable {
      pendingSetLayoutTask.getAndSet(null)?.run()

      if (toolWindowPane == null) {
        if (!ApplicationManager.getApplication().isUnitTestMode) {
          LOG.warn("ProjectFrameAllocator is not used - use PlatformProjectOpenProcessor.openExistingProject to open project in a correct way")
        }
        init((WindowManager.getInstance() as WindowManagerImpl).allocateFrame(project))

        // cannot be executed because added layered pane is not yet validated and size is not known
        ApplicationManager.getApplication().invokeLater(Runnable {
          pendingSetLayoutTask.getAndSet(null)?.run()
          initToolWindows(list)
        }, project.disposed)
      }
      else {
        initToolWindows(list)
      }

      ToolWindowEP.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<ToolWindowEP> {
        override fun extensionAdded(extension: ToolWindowEP, pluginDescriptor: PluginDescriptor) {
          initToolWindow(extension)
        }

        override fun extensionRemoved(extension: ToolWindowEP, pluginDescriptor: PluginDescriptor) {
          doUnregisterToolWindow(extension.id)
        }
      }, project)
    }, project.disposed)
  }

  private fun initToolWindows(list: MutableList<ToolWindowEP>) {
    runActivity("toolwindow creating") {
      commandProcessor.activate()
      for (bean in list) {
        try {
          doInitToolWindow(bean, bean.toolWindowFactory)
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (t: Throwable) {
          LOG.error("failed to init toolwindow ${bean.factoryClass}", t)
        }
      }
    }

    service<ToolWindowManagerAppLevelHelper>()
  }

  override fun initToolWindow(bean: ToolWindowEP) {
    val condition = bean.condition
    if (condition != null && !condition.value(project)) {
      return
    }

    val toolWindowFactory = bean.toolWindowFactory
    if (!toolWindowFactory.isApplicable(project)) {
      return
    }
    doInitToolWindow(bean, toolWindowFactory)
  }

  private fun doInitToolWindow(bean: ToolWindowEP, factory: ToolWindowFactory) {
    val toolWindowAnchor = ToolWindowAnchor.fromText(bean.anchor)
    doRegisterToolWindow(RegisterToolWindowTask(id = bean.id, anchor = toolWindowAnchor,
                                                            canCloseContent = bean.canCloseContents,
                                                            canWorkInDumbMode = DumbService.isDumbAware(factory),
                                                            shouldBeAvailable = factory.shouldBeAvailable(project)), factory, bean)
  }

  fun projectClosed() {
    if (frame == null) {
      return
    }

    frame!!.releaseFrame()
    val commands = mutableListOf<Runnable>()
    appendUpdateRootPane(commands)
    // hide all tool windows
    idToEntry.forEach { (id, entry) ->
      deactivateToolWindowImpl(layout.getInfo(id) ?: return@forEach, entry, true, commands)
    }
    // do not notify - project is disposed
    commandProcessor.execute(commands)
    frame = null
  }

  override fun addToolWindowManagerListener(listener: ToolWindowManagerListener) {
    dispatcher.addListener(listener)
  }

  override fun addToolWindowManagerListener(listener: ToolWindowManagerListener, parentDisposable: Disposable) {
    project.messageBus.connect(parentDisposable).subscribe(ToolWindowManagerListener.TOPIC, listener)
  }

  override fun removeToolWindowManagerListener(listener: ToolWindowManagerListener) {
    dispatcher.removeListener(listener)
  }

  fun execute(commands: List<Runnable>) {
    if (commands.isEmpty()) {
      return
    }

    commandProcessor.execute(commands)

    fireStateChanged()
  }

  override fun activateEditorComponent() {
    focusDefaultElementInSelectedEditor()
  }

  private fun deactivateWindows(idToIgnore: String, commands: MutableList<Runnable>) {
    idToEntry.forEach { (id, entry) ->
      if (idToIgnore != id) {
        val info = layout.getInfo(id) ?: return@forEach
        deactivateToolWindowImpl(info, entry, isToHideOnDeactivation(info), commands)
      }
    }
  }

  /**
   * Helper method. It makes window visible, activates it and request focus into the tool window.
   * But it doesn't deactivate other tool windows. Use `prepareForActivation` method to
   * deactivates other tool windows.
   *
   * @param dirtyMode if `true` then all UI operations are performed in "dirty" mode.
   * It means that UI isn't validated and repainted just after each add/remove operation.
   */
  private fun showAndActivate(entry: ToolWindowEntry, dirtyMode: Boolean, autoFocusContents: Boolean, commands: MutableList<Runnable>) {
    if (!entry.toolWindow.isAvailable) {
      return
    }

    // show activated
    val info = getRegisteredMutableInfoOrLogError(entry.id)
    var toApplyInfo = false
    if (!info.isActive) {
      info.isActive = true
      toApplyInfo = true
    }

    showToolWindowImpl(entry, dirtyMode, commands)
    // activate
    if (toApplyInfo) {
      appendApplyWindowInfoCmd(info, entry, commands)
      activeStack.push(entry)
    }

    if (autoFocusContents && ApplicationManager.getApplication().isActive) {
      appendRequestFocusInToolWindowCmd(entry.id, commands)
    }
  }

  internal fun activateToolWindow(id: String, runnable: Runnable?, autoFocusContents: Boolean, forced: Boolean) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val activity = UiActivity.Focus("toolWindow:$id")
    UiActivityMonitor.getInstance().addActivity(project, activity, ModalityState.NON_MODAL)
    activateToolWindow(idToEntry.get(id)!!, forced, autoFocusContents)
    invokeLater(Runnable {
      runnable?.run()
      UiActivityMonitor.getInstance().removeActivity(project, activity)
    })
  }

  internal fun activateToolWindow(entry: ToolWindowEntry, forced: Boolean, autoFocusContents: Boolean) {
    if (LOG.isDebugEnabled) {
      LOG.debug("enter: activateToolWindow($entry)")
    }

    ApplicationManager.getApplication().assertIsDispatchThread()
    val commands = mutableListOf<Runnable>()
    activateToolWindowImpl(entry, commands, forced, autoFocusContents)
    execute(commands)
  }

  private fun activateToolWindowImpl(entry: ToolWindowEntry, commands: MutableList<Runnable>, forced: Boolean, autoFocusContents: Boolean) {
    var effectiveAutoFocusContents = autoFocusContents
    val id = entry.id
    ToolWindowCollector.recordActivation(id, layout.getInfo(id))
    effectiveAutoFocusContents = effectiveAutoFocusContents && forced
    LOG.debug { "enter: activateToolWindowImpl($id)" }
    if (!entry.toolWindow.isAvailable) {
      // Tool window can be "logically" active but not focused. For example,
      // when the user switched to another application. So we just need to bring
      // tool window's window to front.
      if (effectiveAutoFocusContents && !entry.toolWindow.hasFocus) {
        appendRequestFocusInToolWindowCmd(id, commands)
      }
      return
    }

    deactivateWindows(id, commands)
    showAndActivate(entry, dirtyMode = false, autoFocusContents = effectiveAutoFocusContents, commands = commands)
  }

  // mutate operation must use info from layout and not from decorator
  private fun getRegisteredMutableInfoOrLogError(id: String): WindowInfoImpl {
    val info = layout.getInfo(id)
               ?: throw IllegalThreadStateException("window with id=\"$id\" is unknown")
    if (!isToolWindowRegistered(id)) {
      LOG.error("window with id=\"$id\" isn't registered")
    }
    return info
  }

  /**
   * Helper method. It deactivates (and hides) window with specified `id`.
   */
  private fun deactivateToolWindowImpl(info: WindowInfoImpl, entry: ToolWindowEntry, shouldHide: Boolean, commands: MutableList<Runnable>) {
    LOG.debug { "enter: deactivateToolWindowImpl(${info.id},$shouldHide)" }
    if (shouldHide) {
      appendRemoveDecoratorCommand(info, entry, false, commands)
    }
    info.isActive = false
    appendApplyWindowInfoCmd(info, entry, commands)
    checkInvariants("Info: $info; shouldHide: $shouldHide")
  }

  override val toolWindowIds: Array<String>
    get() = idToEntry.keys.toTypedArray()

  override val activeToolWindowId: String?
    get() {
      ApplicationManager.getApplication().assertIsDispatchThread()
      return idToEntry.values.firstOrNull {
        it.readOnlyWindowInfo.isActive
      }?.toolWindow?.id
    }

  override fun getLastActiveToolWindowId(): String? {
    return getLastActiveToolWindowId(null)
  }

  override fun getLastActiveToolWindowId(condition: Condition<in JComponent>?): String? {
    ApplicationManager.getApplication().assertIsDispatchThread()
    var lastActiveToolWindowId: String? = null
    for (i in 0 until activeStack.persistentSize) {
      val toolWindow = activeStack.peekPersistent(i).toolWindow
      if (toolWindow.isAvailable) {
        if (condition == null || condition.value(toolWindow.component)) {
          lastActiveToolWindowId = toolWindow.id
          break
        }
      }
    }
    return lastActiveToolWindowId
  }

  /**
   * @return floating decorator for the tool window with specified `ID`.
   */
  private fun getFloatingDecorator(id: String): FloatingDecorator? {
    return idToEntry.get(id)?.floatingDecorator
  }

  /**
   * @return windowed decorator for the tool window with specified `ID`.
   */
  private fun getWindowedDecorator(id: String): FrameWrapper? {
    return idToEntry.get(id)?.windowedDecorator
  }

  /**
   * @return tool button for the window with specified `ID`.
   */
  fun getStripeButton(id: String) = idToEntry.get(id)!!.stripeButton

  override fun getIdsOn(anchor: ToolWindowAnchor) = getVisibleToolWindowsOn(anchor).map { it.id }.toList()

  @ApiStatus.Internal
  fun getToolWindowsOn(anchor: ToolWindowAnchor, excludedId: String) = getVisibleToolWindowsOn(anchor).filter { it.id != excludedId  }.map { it.toolWindow }.toList()

  @ApiStatus.Internal
  fun getDockedInfoAt(anchor: ToolWindowAnchor?, side: Boolean): WindowInfo? {
    for (entry in idToEntry.values) {
      val info = entry.readOnlyWindowInfo
      if (info.isVisible && info.isDocked && info.anchor == anchor && side == info.isSplit) {
        return info
      }
    }
    return null
  }

  override fun getLocationIcon(id: String, fallbackIcon: Icon): Icon {
    val info = layout.getInfo(id)
    if (info == null) {
      return fallbackIcon
    }

    val type = info.type
    if (type == ToolWindowType.FLOATING || type == ToolWindowType.WINDOWED) {
      return AllIcons.Actions.MoveToWindow
    }

    val anchor = info.anchor
    val splitMode = info.isSplit
    return when {
      ToolWindowAnchor.BOTTOM == anchor -> {
        if (splitMode) AllIcons.Actions.MoveToBottomRight else AllIcons.Actions.MoveToBottomLeft
      }
      ToolWindowAnchor.LEFT == anchor -> {
        if (splitMode) AllIcons.Actions.MoveToLeftBottom else AllIcons.Actions.MoveToLeftTop
      }
      ToolWindowAnchor.RIGHT == anchor -> {
        if (splitMode) AllIcons.Actions.MoveToRightBottom else AllIcons.Actions.MoveToRightTop
      }
      ToolWindowAnchor.TOP == anchor -> {
        if (splitMode) AllIcons.Actions.MoveToTopRight else AllIcons.Actions.MoveToTopLeft
      }
      else -> fallbackIcon
    }
  }

  private fun getVisibleToolWindowsOn(anchor: ToolWindowAnchor): Sequence<ToolWindowEntry> {
    return layout.getAllInfos(anchor).asSequence().mapNotNull { each ->
      val entry = idToEntry.get(each.id ?: return@mapNotNull null) ?: return@mapNotNull null
      if (entry.toolWindow.isAvailable || UISettings.instance.alwaysShowWindowsButton) entry else null
    }
  }

  // cannot be ToolWindowEx because of backward compatibility
  override fun getToolWindow(id: String): ToolWindow? {
    return idToEntry.get(id)?.toolWindow
  }

  fun showToolWindow(id: String) {
    LOG.debug { "enter: showToolWindow($id)" }
    ApplicationManager.getApplication().assertIsDispatchThread()
    val commands = mutableListOf<Runnable>()
    showToolWindowImpl(idToEntry.get(id)!!, false, commands)
    execute(commands)
  }

  override fun hideToolWindow(id: String, hideSide: Boolean) {
    hideToolWindow(id, hideSide, true)
  }

  fun hideToolWindow(id: String, hideSide: Boolean, moveFocus: Boolean) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    val entry = idToEntry.get(id)!!
    val info = getRegisteredMutableInfoOrLogError(id)
    val commands = mutableListOf<Runnable>()
    val wasActive = info.isActive
    // hide and deactivate
    deactivateToolWindowImpl(info, entry, true, commands)
    if (hideSide && info.type != ToolWindowType.FLOATING && info.type != ToolWindowType.WINDOWED) {
      for (each in getVisibleToolWindowsOn(info.anchor)) {
        activeStack.remove(each, true)
      }
      if (isStackEnabled) {
        while (!sideStack.isEmpty(info.anchor)) {
          sideStack.pop(info.anchor)
        }
      }
      idToEntry.forEach { (otherId, otherEntry) ->
        val otherInfo = layout.getInfo(otherId) ?: return@forEach
        if (otherInfo.isVisible && otherInfo.anchor == info.anchor) {
          deactivateToolWindowImpl(otherInfo, otherEntry, true, commands)
        }
      }
    }
    else {
      // first of all we have to find tool window that was located at the same side and was hidden
      if (isStackEnabled) {
        var info2: WindowInfoImpl? = null
        while (!sideStack.isEmpty(info.anchor)) {
          val storedInfo = sideStack.pop(info.anchor)
          if (storedInfo.isSplit != info.isSplit) {
            continue
          }
          val currentInfo = getRegisteredMutableInfoOrLogError(storedInfo.id!!)
          // SideStack contains copies of real WindowInfos. It means that
          // these stored infos can be invalid. The following loop removes invalid WindowInfos.
          if (storedInfo.anchor == currentInfo.anchor && storedInfo.type == currentInfo.type && storedInfo.isAutoHide == currentInfo.isAutoHide) {
            info2 = storedInfo
            break
          }
        }
        if (info2 != null) {
          showToolWindowImpl(idToEntry.get(info2.id!!)!!, false, commands)
        }
      }
      // If we hide currently active tool window then we should activate the previous
      // one which is located in the tool window stack.
      // Activate another tool window if no active tool window exists and
      // window stack is enabled.
      activeStack.remove(entry, false) // hidden window should be at the top of stack
      if (wasActive && moveFocus && !activeStack.isEmpty) {
        val toBeActivated = activeStack.pop()
        if (getRegisteredMutableInfoOrLogError(toBeActivated.id).isVisible || isStackEnabled) {
          activateToolWindowImpl(toBeActivated, commands, false, true)
        }
        else {
          focusToolWindowByDefault(entry)
        }
      }
    }
    execute(commands)
  }

  /**
   * @param dirtyMode if `true` then all UI operations are performed in dirty mode.
   */
  private fun showToolWindowImpl(entry: ToolWindowEntry, dirtyMode: Boolean, commands: MutableList<Runnable>) {
    val id = entry.id
    if (entry.readOnlyWindowInfo.type == ToolWindowType.WINDOWED && entry.toolWindow.getComponentIfInitialized() != null) {
      UIUtil.toFront(UIUtil.getWindow(entry.toolWindow.component))
    }

    if (entry.readOnlyWindowInfo.isVisible || !entry.toolWindow.isAvailable) {
      return
    }

    val toBeShownInfo = layout.getInfo(id) ?: throw IllegalThreadStateException("window with id=\"$id\" is unknown")
    toBeShownInfo.isVisible = true

    entry.toolWindow.ensureContentInitialized()

    if (entry.readOnlyWindowInfo.isFloating) {
      commands.add(AddFloatingDecoratorCmd(entry, toBeShownInfo))
    }
    else if (entry.readOnlyWindowInfo.type == ToolWindowType.WINDOWED) {
      commands.add(AddWindowedDecoratorCmd(entry, toBeShownInfo))
    }
    else {
      // docked and sliding windows
      // If there is tool window on the same side then we have to hide it, i.e.
      // clear place for tool window to be shown.
      //
      // We store WindowInfo of hidden tool window in the SideStack (if the tool window
      // is docked and not auto-hide one). Therefore it's possible to restore the
      // hidden tool window when showing tool window will be closed.
      for ((otherId, otherEntry) in idToEntry) {
        if (id == otherId) {
          continue
        }

        val otherInfo = layout.getInfo(otherId) ?: continue
        if (otherInfo.isVisible && (otherInfo.type == toBeShownInfo.type) && (otherInfo.anchor == toBeShownInfo.anchor) && (otherInfo.isSplit == toBeShownInfo.isSplit)) {
          // hide and deactivate tool window
          otherInfo.isVisible = false
          appendRemoveDecoratorCmd(otherInfo, entry, false, commands)
          if (otherInfo.isActive) {
            otherInfo.isActive = false
          }
          appendApplyWindowInfoCmd(otherInfo, otherEntry, commands)
          // store WindowInfo into the SideStack
          if (isStackEnabled && otherInfo.isDocked && !otherInfo.isAutoHide) {
            sideStack.push(otherInfo)
          }
        }
      }

      commands.add(toolWindowPane!!.createAddDecoratorCmd(entry.toolWindow.decoratorComponent!!, toBeShownInfo, dirtyMode))
      // Remove tool window from the SideStack.
      if (isStackEnabled) {
        sideStack.remove(id)
      }
    }

    if (!toBeShownInfo.isShowStripeButton) {
      toBeShownInfo.isShowStripeButton = true
    }
    appendApplyWindowInfoCmd(toBeShownInfo, entry, commands)
    checkInvariants("Id: $id; dirtyMode: $dirtyMode")
  }

  override fun registerToolWindow(task: RegisterToolWindowTask): ToolWindowImpl {
    val toolWindow = doRegisterToolWindow(task, null, null).toolWindow
    toolWindowPane!!.validate()
    toolWindowPane!!.repaint()
    return toolWindow
  }

  private fun doRegisterToolWindow(task: RegisterToolWindowTask, contentFactory: ToolWindowFactory?, bean: ToolWindowEP?): ToolWindowEntry {
    val toolWindowPane = toolWindowPane
    if (toolWindowPane == null) {
      init((WindowManager.getInstance() as WindowManagerImpl).allocateFrame(project))
    }

    if (LOG.isDebugEnabled) {
      LOG.debug("enter: installToolWindow($task)")
    }

    ApplicationManager.getApplication().assertIsDispatchThread()
    if (idToEntry.containsKey(task.id)) {
      throw IllegalArgumentException("window with id=\"${task.id}\" is already registered")
    }

    val info = layout.getOrCreate(task)
    val wasActive = info.isActive
    val wasVisible = info.isVisible
    info.isActive = false
    info.isVisible = false

    val disposable = Disposer.newDisposable(task.id)
    Disposer.register(project, disposable)

    val windowInfoSnapshot = info.copy()

    var icon: Icon? = null
    if (bean?.icon != null) {
      icon = IconLoader.findIcon(bean.icon, contentFactory!!.javaClass)
      if (icon == null) {
        try {
          icon = IconLoader.getIcon(bean.icon)
        }
        catch (ignored: Exception) {
          icon = EmptyIcon.ICON_13
        }
      }
    }

    val toolWindow = ToolWindowImpl(this, task.id, task.canCloseContent, task.canWorkInDumbMode, task.component, disposable,
      windowInfoSnapshot, contentFactory,
      icon = if (icon == null) null else ToolWindowIcon(icon, task.id),
      isAvailable = task.shouldBeAvailable)

    val button = StripeButton(toolWindowPane!!, windowInfoSnapshot, toolWindow)
    val commands = mutableListOf<Runnable>()

    toolWindowPane.addStripeButton(button, info.anchor, layout.MyStripeButtonComparator(info.anchor, this))

    val entry = ToolWindowEntry(button, toolWindow, disposable, windowInfoSnapshot)
    idToEntry.put(task.id, entry)

    // If preloaded info is visible or active then we have to show/activate the installed
    // tool window. This step has sense only for windows which are not in the auto hide
    // mode. But if tool window was active but its mode doesn't allow to activate it again
    // (for example, tool window is in auto hide mode) then we just activate editor component.
    if (!info.isAutoHide && contentFactory != null /* not null on init tool window from EP */) {
      // do not activate tool window that is the part of project frame - default component should be focused
      if (wasActive && (info.type == ToolWindowType.DOCKED || info.type == ToolWindowType.FLOATING)) {
        activateToolWindowImpl(entry, commands, forced = true, autoFocusContents = true)
      }
      else if (wasVisible) {
        showToolWindowImpl(entry, dirtyMode = false, commands = commands)
      }

      if (!info.isSplit && bean != null && bean.secondary && !info.isFromPersistentSettings) {
        setSideTool(entry, info, true, commands)
      }
    }

    // do not fire stateChanged - listeners should rely on toolWindowRegistered instead
    commandProcessor.execute(commands)

    ActivateToolWindowAction.ensureToolWindowActionRegistered(toolWindow)

    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowRegistered(task.id)
    return entry
  }

  @Suppress("OverridingDeprecatedMember")
  override fun unregisterToolWindow(id: String) {
    doUnregisterToolWindow(id)
  }

  internal fun doUnregisterToolWindow(id: String) {
    if (LOG.isDebugEnabled) {
      LOG.debug("enter: unregisterToolWindow($id)")
    }

    ApplicationManager.getApplication().assertIsDispatchThread()

    val entry = idToEntry.remove(id) ?: return
    val toolWindow = entry.toolWindow

    val info = layout.getInfo(id)
    if (info != null) {
      // remove decorator and tool button from the screen
      val commands = mutableListOf<Runnable>()
      appendRemoveDecoratorCommand(info, entry, false, commands)
      // Save recent appearance of tool window
      activeStack.remove(entry, true)
      if (isStackEnabled) {
        sideStack.remove(id)
      }
      appendRemoveButtonCmd(entry, info, commands)
      appendApplyWindowInfoCmd(info, entry, commands)
      commandProcessor.execute(commands)
    }

    if (!project.isDisposed) {
      project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowUnregistered(id, (toolWindow))
    }

    Disposer.dispose(entry.disposable)
  }

  private fun appendRemoveDecoratorCommand(info: WindowInfoImpl, entry: ToolWindowEntry, dirtyMode: Boolean, commands: MutableList<Runnable>) {
    if (!info.isVisible) {
      return
    }

    info.isVisible = false
    when (info.type) {
      ToolWindowType.FLOATING -> {
        val floatingDecorator = entry.floatingDecorator
        if (floatingDecorator != null) {
          info.floatingBounds = floatingDecorator.bounds
          commands.add(Runnable {
            floatingDecorator.dispose()
          })
        }
      }
      ToolWindowType.WINDOWED -> {
        val windowedDecorator = entry.windowedDecorator
        if (windowedDecorator != null) {
          entry.windowedDecorator = null
          val frame = windowedDecorator.frame
          if (frame.isShowing) {
            val maximized = (frame as JFrame).extendedState == Frame.MAXIMIZED_BOTH
            if (maximized) {
              frame.extendedState = Frame.NORMAL
              frame.invalidate()
              frame.revalidate()
            }

            val bounds = getRootBounds(frame)
            info.floatingBounds = bounds
            info.isMaximized = maximized
          }

          commands.add(Runnable {
            Disposer.dispose(windowedDecorator)
          })
        }
      }
      else -> appendRemoveDecoratorCmd(info, entry, dirtyMode, commands)
    }
  }

  override fun getLayout(): DesktopLayout {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return layout
  }

  override fun setLayoutToRestoreLater(layout: DesktopLayout?) {
    layoutToRestoreLater = layout
  }

  override fun getLayoutToRestoreLater() = layoutToRestoreLater

  override fun setLayout(layout: DesktopLayout) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    // hide tool window that are invisible or its info is not presented in new layout
    if (idToEntry.isEmpty()) {
      this.layout = layout
      return
    }

    val currentInfos = idToEntry.keys.mapNotNull { this.layout.getInfo(it) }
    if (currentInfos.isEmpty()) {
      this.layout = layout
      return
    }

    val commands = mutableListOf<Runnable>()
    for (currentInfo in currentInfos) {
      val id = currentInfo.id!!
      val info = layout.getInfo(id)
      if (currentInfo.isVisible && (info == null || !info.isVisible)) {
        deactivateToolWindowImpl(currentInfo, idToEntry.get(id)!!, true, commands)
      }
    }

    for (currentInfo in currentInfos) {
      val currentId = currentInfo.id!!
      val info = layout.getInfo(currentId) ?: continue
      if (currentInfo.anchor != info.anchor || currentInfo.order != info.order) {
        setToolWindowAnchorImpl(currentId, info.anchor, info.order, commands)
      }

      if (currentInfo.type != info.type) {
        setToolWindowTypeImpl(currentId, info.type, commands)
      }

      copyWindowOptions(currentInfo, info, commands)

      if (info.isVisible) {
        showToolWindowImpl(idToEntry.get(currentId)!!, false, commands)
      }
    }
    execute(commands)
    checkInvariants("")
    this.layout = layout
  }

  override fun invokeLater(runnable: Runnable) {
    commandProcessor.execute(listOf(Runnable {
      ApplicationManager.getApplication().invokeLater(runnable, project.disposed)
    }))
  }

  override val focusManager: IdeFocusManager
    get() = IdeFocusManager.getInstance(project)!!

  override fun canShowNotification(toolWindowId: String): Boolean {
    return toolWindowPane?.getStripeFor(toolWindowId)?.getButtonFor(toolWindowId) != null
  }

  override fun notifyByBalloon(toolWindowId: String, type: MessageType, htmlBody: String) {
    notifyByBalloon(toolWindowId, type, htmlBody, null, null)
  }

  override fun notifyByBalloon(toolWindowId: String, type: MessageType, htmlBody: String, icon: Icon?, listener: HyperlinkListener?) {
    val entry = idToEntry.get(toolWindowId)!!
    val existing = entry.balloon
    if (existing != null) {
      Disposer.dispose(existing)
    }

    val stripe = toolWindowPane!!.getStripeFor(toolWindowId) ?: return
    if (!entry.toolWindow.isAvailable) {
      entry.toolWindow.isPlaceholderMode = true
      stripe.updatePresentation()
      stripe.revalidate()
      stripe.repaint()
    }

    val anchor = entry.readOnlyWindowInfo.anchor
    val position = Ref.create(Balloon.Position.below)
    when {
      ToolWindowAnchor.TOP == anchor -> position.set(Balloon.Position.below)
      ToolWindowAnchor.BOTTOM == anchor -> position.set(Balloon.Position.above)
      ToolWindowAnchor.LEFT == anchor -> position.set(Balloon.Position.atRight)
      ToolWindowAnchor.RIGHT == anchor -> position.set(Balloon.Position.atLeft)
    }

    val listenerWrapper = BalloonHyperlinkListener(listener)
    val balloon = JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(htmlBody.replace("\n", "<br>"), icon, type.titleForeground, type.popupBackground, listenerWrapper)
      .setBorderColor(type.borderColor)
      .setHideOnClickOutside(false)
      .setHideOnFrameResize(false)
      .createBalloon() as BalloonImpl
    NotificationsManagerImpl.frameActivateBalloonListener(balloon) {
      AppExecutorUtil.getAppScheduledExecutorService().schedule(Runnable { balloon.setHideOnClickOutside(true) }, 100, TimeUnit.MILLISECONDS)
    }

    listenerWrapper.balloon = balloon
    entry.balloon = balloon
    Disposer.register(balloon, Disposable {
      entry.toolWindow.isPlaceholderMode = false
      stripe.updatePresentation()
      stripe.revalidate()
      stripe.repaint()
      entry.balloon = null
    })
    Disposer.register(entry.disposable, balloon)
    execute(listOf(Runnable {
        val button = stripe.getButtonFor(toolWindowId)
        LOG.assertTrue(button != null, ("Button was not found, popup won't be shown. Toolwindow id: $toolWindowId, message: $htmlBody, message type: $type"))
        if (button == null) {
          return@Runnable
        }

        val show = Runnable {
          val tracker: PositionTracker<Balloon>
          if (button.isShowing) {
            tracker = object : PositionTracker<Balloon>(button) {
              override fun recalculateLocation(`object`: Balloon): RelativePoint? {
                val stripeButton = toolWindowPane!!.getStripeFor(toolWindowId)?.getButtonFor(toolWindowId) ?: return null
                if (getToolWindow(toolWindowId)?.anchor != anchor) {
                  `object`.hide()
                  return null
                }
                return RelativePoint(stripeButton, Point(stripeButton.bounds.width / 2, stripeButton.height / 2 - 2))
              }
            }
          }
          else {
            tracker = object : PositionTracker<Balloon>(toolWindowPane) {
              override fun recalculateLocation(`object`: Balloon): RelativePoint {
                val bounds = toolWindowPane!!.bounds
                val target = StartupUiUtil.getCenterPoint(bounds, Dimension(1, 1))
                when {
                  ToolWindowAnchor.TOP == anchor -> target.y = 0
                  ToolWindowAnchor.BOTTOM == anchor -> target.y = bounds.height - 3
                  ToolWindowAnchor.LEFT == anchor -> target.x = 0
                  ToolWindowAnchor.RIGHT == anchor -> target.x = bounds.width
                }
                return RelativePoint(toolWindowPane!!, target)
              }
            }
          }
          if (!balloon.isDisposed) {
            balloon.show(tracker, position.get())
          }
        }

        if (button.isValid) {
          show.run()
        }
        else {
          SwingUtilities.invokeLater(show)
        }
    }))
  }

  override fun getToolWindowBalloon(id: String) = idToEntry.get(id)?.balloon

  override val isEditorComponentActive: Boolean
    get() {
      ApplicationManager.getApplication().assertIsDispatchThread()
      return ComponentUtil.getParentOfType(EditorsSplitters::class.java, focusManager.focusOwner) != null
    }

  fun getToolWindowAnchor(id: String) = getRegisteredMutableInfoOrLogError(id).anchor

  fun setToolWindowAnchor(id: String, anchor: ToolWindowAnchor) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    setToolWindowAnchor(id, anchor, -1)
  }

  // used by Rider
  @Suppress("MemberVisibilityCanBePrivate")
  fun setToolWindowAnchor(id: String, anchor: ToolWindowAnchor, order: Int) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val commandList = mutableListOf<Runnable>()
    setToolWindowAnchorImpl(id, anchor, order, commandList)
    execute(commandList)
  }

  private fun setToolWindowAnchorImpl(id: String, anchor: ToolWindowAnchor, order: Int, commands: MutableList<Runnable>) {
    val info = getRegisteredMutableInfoOrLogError(id)
    if (anchor == info.anchor && order == info.order) {
      return
    }

    val entry = idToEntry.get(id)!!

    // if tool window isn't visible or only order number is changed then just remove/add stripe button
    if (!info.isVisible || anchor == info.anchor || info.isFloating || info.type == ToolWindowType.WINDOWED) {
      appendRemoveButtonCmd(entry, info, commands)
      layout.setAnchor(id, anchor, order)
      // update infos for all window. Actually we have to update only infos affected by
      // setAnchor method
      idToEntry.forEach { (otherId, otherEntry) ->
        appendApplyWindowInfoCmd(layout.getInfo(otherId) ?: return@forEach, otherEntry, commands)
      }
      addAddStripeButtonCommand(getStripeButton(id), info, commands)
    }
    else {
      // for docked and sliding windows we have to move buttons and window's decorators
      info.isVisible = false
      appendRemoveDecoratorCmd(info, entry, false, commands)
      appendRemoveButtonCmd(entry, info, commands)
      layout.setAnchor(id, anchor, order)
      // update infos for all window. Actually we have to update only infos affected by
      // setAnchor method
      idToEntry.forEach { (otherId, otherEntry) ->
        appendApplyWindowInfoCmd(layout.getInfo(otherId) ?: return@forEach, otherEntry, commands)
      }
      addAddStripeButtonCommand(getStripeButton(id), info, commands)
      showToolWindowImpl(entry, false, commands)
      if (info.isActive) {
        appendRequestFocusInToolWindowCmd(id, commands)
      }
    }
  }

  fun isSplitMode(id: String): Boolean {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return getRegisteredMutableInfoOrLogError(id).isSplit
  }

  fun getContentUiType(id: String): ToolWindowContentUiType {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return getRegisteredMutableInfoOrLogError(id).contentUiType
  }

  internal fun setSideTool(id: String, isSplit: Boolean) {
    val entry = idToEntry.get(id)!!
    val commands = mutableListOf<Runnable>()
    setSideTool(entry, entry.readOnlyWindowInfo, isSplit, commands)
    execute(commands)
  }

  private fun setSideTool(entry: ToolWindowEntry, info: WindowInfo, isSplit: Boolean, commands: MutableList<Runnable>) {
    if (isSplit == info.isSplit) {
      return
    }

    val id = entry.id
    layout.setSplitMode(id, isSplit)
    val wasActive = info.isActive
    val wasVisible = info.isVisible
    // we should hide the window and show it in a 'new place' to automatically hide possible window that is already located in a 'new place'
    if (wasActive || wasVisible) {
      hideToolWindow(id, false)
    }

    idToEntry.forEach { (otherId, otherEntry) ->
      val otherInfo = layout.getInfo(otherId) ?: return@forEach
      appendApplyWindowInfoCmd(otherInfo, otherEntry, commands)
    }
    if (wasVisible || wasActive) {
      showToolWindowImpl(entry, true, commands)
    }
    if (wasActive) {
      activateToolWindowImpl(entry, commands, true, true)
    }
    commands.add(toolWindowPane!!.createUpdateButtonPositionCmd(idToEntry.get(id)!!.readOnlyWindowInfo.anchor))
  }

  fun setContentUiType(id: String, type: ToolWindowContentUiType) {
    val info = getRegisteredMutableInfoOrLogError(id)
    info.contentUiType = type
    val commands = mutableListOf<Runnable>()
    appendApplyWindowInfoCmd(info, idToEntry.get(info.id!!)!!, commands)
    execute(commands)
  }

  fun setSideToolAndAnchor(id: String, anchor: ToolWindowAnchor, order: Int, isSide: Boolean) {
    val entry = idToEntry.get(id)!!
    hideToolWindow(id, false)
    layout.setSplitMode(id, isSide)
    setToolWindowAnchor(id, anchor, order)
    activateToolWindow(entry, false, false)
  }

  fun getToolWindowInternalType(id: String): ToolWindowType {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return getRegisteredMutableInfoOrLogError(id).internalType
  }

  fun getToolWindowType(id: String) = getRegisteredMutableInfoOrLogError(id).type

  protected open fun fireStateChanged() {
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).stateChanged(this)
  }

  fun isToolWindowActive(id: String): Boolean {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return layout.getInfo(id)?.isActive ?: false
  }

  fun isToolWindowAutoHide(id: String): Boolean {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return idToEntry.get(id)?.readOnlyWindowInfo?.isAutoHide ?: false
  }

  fun isToolWindowVisible(id: String): Boolean {
    return idToEntry.get(id)?.readOnlyWindowInfo?.isVisible ?: false
  }

  fun setToolWindowAutoHide(id: String, autoHide: Boolean) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val commandList = mutableListOf<Runnable>()
    setToolWindowAutoHideImpl(id, autoHide, commandList)
    execute(commandList)
  }

  private fun setToolWindowAutoHideImpl(id: String, autoHide: Boolean, commands: MutableList<Runnable>) {
    val info = getRegisteredMutableInfoOrLogError(id)
    if (info.isAutoHide == autoHide) {
      return
    }

    info.isAutoHide = autoHide
    val entry = idToEntry.get(info.id!!)
    appendApplyWindowInfoCmd(info, entry!!, commands)
    if (info.isVisible) {
      deactivateWindows(id, commands)
      showAndActivate(entry, false, true, commands)
    }
  }

  private fun copyWindowOptions(currentInfo: WindowInfoImpl, origin: WindowInfoImpl, commands: MutableList<Runnable>) {
    val id = origin.id!!
    var changed = false
    if (currentInfo.isAutoHide != origin.isAutoHide) {
      currentInfo.isAutoHide = origin.isAutoHide
      changed = true
    }
    if (currentInfo.weight != origin.weight) {
      currentInfo.weight = origin.weight
      changed = true
    }
    if (currentInfo.sideWeight != origin.sideWeight) {
      currentInfo.sideWeight = origin.sideWeight
      changed = true
    }
    if (currentInfo.contentUiType !== origin.contentUiType) {
      currentInfo.contentUiType = origin.contentUiType
      changed = true
    }

    if (changed) {
      val entry = idToEntry.get(id)!!
      appendApplyWindowInfoCmd(currentInfo, entry, commands)
      if (currentInfo.isVisible) {
        deactivateWindows(id, commands)
        showAndActivate(entry, false, true, commands)
      }
    }
  }

  fun setToolWindowType(id: String, type: ToolWindowType) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val commands = mutableListOf<Runnable>()
    setToolWindowTypeImpl(id, type, commands)
    execute(commands)
  }

  private fun setToolWindowTypeImpl(id: String, type: ToolWindowType, commands: MutableList<Runnable>) {
    val entry = idToEntry.get(id)!!
    if (entry.readOnlyWindowInfo.type == type) {
      return
    }

    val info = getRegisteredMutableInfoOrLogError(id)
    if (entry.readOnlyWindowInfo.isVisible) {
      val dirtyMode = entry.readOnlyWindowInfo.type == ToolWindowType.DOCKED || entry.readOnlyWindowInfo.type == ToolWindowType.SLIDING
      appendRemoveDecoratorCommand(info, entry, dirtyMode, commands)
      info.type = type
      appendApplyWindowInfoCmd(info, entry, commands)
      deactivateWindows(id, commands)
      showAndActivate(entry, dirtyMode, true, commands)
      appendUpdateRootPane(commands)
    }
    else {
      info.type = type
      appendApplyWindowInfoCmd(info, entry, commands)
    }
  }

  private fun appendApplyWindowInfoCmd(info: WindowInfoImpl, entry: ToolWindowEntry, commands: MutableList<Runnable>) {
    val newInfo = info.copy()
    commands.add(Runnable {
      entry.readOnlyWindowInfo = newInfo
      entry.stripeButton.apply(newInfo)
      entry.toolWindow.applyWindowInfo(newInfo)
    })
  }

  private fun appendRemoveDecoratorCmd(info: WindowInfoImpl, entry: ToolWindowEntry, dirtyMode: Boolean, commands: MutableList<Runnable>) {
    toolWindowPane!!.createRemoveDecoratorCmd(info, entry.toolWindow.decoratorComponent, dirtyMode)?.let { commands.add(it) }
    commands.add(Runnable {
      if (!commands.any { it is RequestFocusInToolWindowCmd }) {
        toolWindowPane!!.transferFocus()
      }
    })
  }

  private fun addAddStripeButtonCommand(button: StripeButton, info: WindowInfoImpl, commands: MutableList<Runnable>) {
    commands.add(Runnable {
      val pane = toolWindowPane!!
      pane.addStripeButton(button, info.anchor, layout.MyStripeButtonComparator(info.anchor, this))
      pane.validate()
      pane.repaint()
    })
  }

  private fun appendRemoveButtonCmd(toolWindowEntry: ToolWindowEntry, info: WindowInfoImpl, commands: MutableList<Runnable>) {
    commands.add(Runnable {
      toolWindowPane!!.removeStripeButton(toolWindowEntry.stripeButton, info.anchor)
    })
  }

  private fun appendRequestFocusInToolWindowCmd(id: String, commandList: MutableList<Runnable>) {
    val entry = idToEntry.get(id) ?: return
    val toolWindow = entry.toolWindow
    commandList.add(RequestFocusInToolWindowCmd(toolWindow))
  }

  private fun appendUpdateRootPane(commands: MutableList<Runnable>) {
    val frame = frame!!
    commands.add(Runnable {
      val rootPane = frame.rootPane ?: return@Runnable
      rootPane.revalidate()
      rootPane.repaint()
    })
  }

  override fun clearSideStack() {
    if (isStackEnabled) {
      sideStack.clear()
    }
  }

  override fun getState(): Element? {
    if (frame == null) {
      // do nothing if the project was not opened
      return null
    }

    val element = Element("state")
    // Save frame's bounds
    // [tav] Where we load these bounds? Should we just remove this code? (because we load frame bounds in WindowManagerImpl.allocateFrame)
    // Anyway, we should save bounds in device space to preserve backward compatibility with the IDE-managed HiDPI mode (see JBUI.ScaleType).
    // However, I won't change =
    //\is code because I can't even test it.
    val frameBounds = frame!!.frame.bounds
    val frameElement = Element(FRAME_ELEMENT)
    element.addContent(frameElement)
    frameElement.setAttribute(X_ATTR, frameBounds.x.toString())
    frameElement.setAttribute(Y_ATTR, frameBounds.y.toString())
    frameElement.setAttribute(WIDTH_ATTR, frameBounds.width.toString())
    frameElement.setAttribute(HEIGHT_ATTR, frameBounds.height.toString())
    frameElement.setAttribute(EXTENDED_STATE_ATTR, frame!!.frame.extendedState.toString())

    // Save whether editor is active or not
    if (isEditorComponentActive) {
      val editorElement = Element(EDITOR_ELEMENT)
      editorElement.setAttribute(ACTIVE_ATTR_VALUE, "true")
      element.addContent(editorElement)
    }

    // Save layout of tool windows
    val layoutElement = layout.writeExternal(DesktopLayout.TAG)
    if (layoutElement != null) {
      element.addContent(layoutElement)
    }

    val layoutToRestoreElement = layoutToRestoreLater?.writeExternal(LAYOUT_TO_RESTORE)
    if (layoutToRestoreElement != null) {
      element.addContent(layoutToRestoreElement)
    }
    return element
  }

  override fun noStateLoaded() {
    val newLayout = WindowManagerEx.getInstanceEx().layout.copy()
    scheduleSetLayout(Runnable {
      layout = newLayout
    })
  }

  override fun loadState(state: Element) {
    for (element in state.children) {
      if (DesktopLayout.TAG == element.name) {
        val layout = DesktopLayout()
        layout.readExternal(element)
        scheduleSetLayout(Runnable {
          setLayout(layout)
        })
      }
      else if (LAYOUT_TO_RESTORE == element.name) {
        layoutToRestoreLater = DesktopLayout()
        layoutToRestoreLater!!.readExternal(element)
      }
    }
    checkInvariants("")
  }

  private fun scheduleSetLayout(task: Runnable) {
    val app = ApplicationManager.getApplication()
    if (app.isDispatchThread) {
      pendingSetLayoutTask.set(null)
      task.run()
    }
    else {
      pendingSetLayoutTask.set(task)
      app.invokeLater(Runnable {
        pendingSetLayoutTask.getAndSet(null)?.run()
      }, project.disposed)
    }
  }

  fun setDefaultState(toolWindow: ToolWindowImpl, anchor: ToolWindowAnchor?, type: ToolWindowType?, floatingBounds: Rectangle?) {
    val info = getRegisteredMutableInfoOrLogError(toolWindow.id)
    if (info.isFromPersistentSettings) {
      return
    }

    if (floatingBounds != null) {
      info.floatingBounds = floatingBounds
    }
    if (anchor != null) {
      toolWindow.setAnchor(anchor, null)
    }
    if (type != null) {
      toolWindow.setType(type, null)
    }
  }

  fun setDefaultContentUiType(toolWindow: ToolWindowImpl, type: ToolWindowContentUiType) {
    val info = getRegisteredMutableInfoOrLogError(toolWindow.id)
    if (info.isFromPersistentSettings) {
      return
    }
    toolWindow.setContentUiType(type, null)
  }

  fun stretchWidth(toolWindow: ToolWindowImpl, value: Int) {
    toolWindowPane!!.stretchWidth(toolWindow, value)
  }

  override fun isMaximized(window: ToolWindow) = toolWindowPane!!.isMaximized(window)

  override fun setMaximized(window: ToolWindow, maximized: Boolean) {
    if (window.type == ToolWindowType.FLOATING && window is ToolWindowImpl) {
      MaximizeActiveDialogAction.doMaximize(getFloatingDecorator(window.id))
      return
    }

    if (window.type == ToolWindowType.WINDOWED && window is ToolWindowImpl) {
      val decorator = getWindowedDecorator(window.id)
      val frame = if (decorator != null && decorator.frame is Frame) decorator.frame as Frame else null
      if (frame != null) {
        val state = frame.state
        if (state == Frame.NORMAL) {
          frame.state = Frame.MAXIMIZED_BOTH
        }
        else if (state == Frame.MAXIMIZED_BOTH) {
          frame.state = Frame.NORMAL
        }
      }
      return
    }
    toolWindowPane!!.setMaximized(window, maximized)
  }

  fun stretchHeight(toolWindow: ToolWindowImpl?, value: Int) {
    toolWindowPane!!.stretchHeight((toolWindow)!!, value)
  }

  private class BalloonHyperlinkListener internal constructor(private val listener: HyperlinkListener?) : HyperlinkListener {
    var balloon: Balloon? = null

    override fun hyperlinkUpdate(e: HyperlinkEvent) {
      val balloon = balloon
      if (balloon != null && e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        balloon.hide()
      }
      listener?.hyperlinkUpdate(e)
    }
  }

  private inner class AddFloatingDecoratorCmd(entry: ToolWindowEntry, info: WindowInfoImpl) : Runnable {
    private val floatingDecorator: FloatingDecorator

    /**
     * Creates floating decorator for specified internal decorator.
     */
    init {
      val frame = frame!!.frame
      floatingDecorator = FloatingDecorator(frame, info.copy(), entry.toolWindow)
      entry.floatingDecorator = floatingDecorator
      val bounds = info.floatingBounds
      if ((bounds != null && bounds.width > 0 && bounds.height > 0 &&
           WindowManager.getInstance().isInsideScreenBounds(bounds.x, bounds.y, bounds.width))) {
        floatingDecorator.bounds = Rectangle(bounds)
      }
      else {
        val decorator = entry.toolWindow.decorator
        // place new frame at the center of main frame if there are no floating bounds
        var size = decorator.size
        if (size.width == 0 || size.height == 0) {
          size = decorator.preferredSize
        }
        floatingDecorator.size = size
        floatingDecorator.setLocationRelativeTo(frame)
      }
    }

    override fun run() {
      @Suppress("DEPRECATION")
      floatingDecorator.show()
    }
  }

  /**
   * This command creates and shows `WindowedDecorator`.
   */
  private inner class AddWindowedDecoratorCmd(entry: ToolWindowEntry, info: WindowInfoImpl) : Runnable {
    private val windowedDecorator: FrameWrapper
    private val shouldBeMaximized: Boolean

    override fun run() {
      windowedDecorator.show(false)
      val window = windowedDecorator.frame
      val rootPane = (window as RootPaneContainer).rootPane
      val rootPaneBounds = rootPane.bounds
      val point = rootPane.locationOnScreen
      val windowBounds = window.bounds
      //Point windowLocation = windowBounds.getLocation();
      //windowLocation.translate(windowLocation.x - point.x, windowLocation.y - point.y);
      window.setLocation(2 * windowBounds.x - point.x, 2 * windowBounds.y - point.y)
      window.setSize(2 * windowBounds.width - rootPaneBounds.width, 2 * windowBounds.height - rootPaneBounds.height)
      if (shouldBeMaximized && window is Frame) {
        (window as Frame).extendedState = Frame.MAXIMIZED_BOTH
      }
      window.toFront()
    }

    /**
     * Creates windowed decorator for specified internal decorator.
     */
    init {
      val decorator = entry.toolWindow.decoratorComponent!!
      windowedDecorator = FrameWrapper(project)
      MnemonicHelper.init((windowedDecorator.frame as RootPaneContainer).contentPane)
      windowedDecorator.setTitle(info.id + " - " + project.name)
      windowedDecorator.setComponent(decorator)

      shouldBeMaximized = info.isMaximized
      val window = windowedDecorator.frame
      val bounds = info.floatingBounds
      if ((bounds != null && bounds.width > 0 && (bounds.height > 0) &&
           WindowManager.getInstance().isInsideScreenBounds(bounds.x, bounds.y, bounds.width))) {
        window.bounds = Rectangle(bounds)
      }
      else {
        // place new frame at the center of main frame if there are no floating bounds
        var size = decorator.size
        if (size.width == 0 || size.height == 0) {
          size = decorator.preferredSize
        }
        window.size = size
        window.setLocationRelativeTo(frame!!.frame)
      }
      entry.windowedDecorator = windowedDecorator
      windowedDecorator.addDisposable {
        val e = idToEntry.get(info.id)
        if (e?.windowedDecorator != null) {
          hideToolWindow(info.id!!, false)
        }
      }
    }
  }

  /**
   * Notifies window manager about focus traversal in a tool window
   */
  internal class ToolWindowFocusWatcher(private val toolWindow: ToolWindowImpl, component: JComponent) : FocusWatcher() {
    private val id = toolWindow.id

    init {
      install(component)
      Disposer.register(toolWindow.disposable, Disposable { deinstall(component) })
    }

    override fun isFocusedComponentChangeValid(component: Component?, cause: AWTEvent?): Boolean {
      return component != null && toolWindow.toolWindowManager.commandProcessor.commandCount == 0
    }

    override fun focusedComponentChanged(component: Component?, cause: AWTEvent?) {
      if (component == null || toolWindow.toolWindowManager.commandProcessor.commandCount > 0) {
        return
      }

      if (!toolWindow.decorator.windowInfo.isActive) {
        IdeFocusManager.getInstance(toolWindow.toolWindowManager.project).doWhenFocusSettlesDown(object : EdtRunnable() {
          override fun runEdt() {
            val manager = toolWindow.toolWindowManager
            val entry = manager.idToEntry.get(id)
            val windowInfo = entry?.readOnlyWindowInfo
            if (windowInfo == null || !windowInfo.isVisible) {
              return
            }
            manager.activateToolWindow(entry, false, false)
          }
        })
      }
    }
  }

  /**
   * Spies on IdeToolWindow properties and applies them to the window
   * state.
   */
  internal fun toolWindowPropertyChanged(toolWindow: ToolWindowImpl, property: ToolWindowProperty) {
    val entry = idToEntry.get(toolWindow.id)

    if (property == ToolWindowProperty.AVAILABLE && !toolWindow.isAvailable && entry?.readOnlyWindowInfo?.isVisible == true) {
      hideToolWindow(toolWindow.id, false)
    }

    val stripeButton = entry?.stripeButton
    if (stripeButton != null) {
      if (property == ToolWindowProperty.ICON) {
        stripeButton.updateIcon()
      }
      else {
        stripeButton.updatePresentation()
      }
    }
    ActivateToolWindowAction.updateToolWindowActionPresentation(toolWindow)
  }

  fun activated(toolWindow: ToolWindowImpl) {
    activateToolWindow(idToEntry.get(toolWindow.id)!!, true, true)
  }

  /**
   * Handles event from decorator and modify weight/floating bounds of the
   * tool window depending on decoration type.
   */
  fun resized(source: InternalDecorator) {
    if (!source.isShowing) {
      // do not recalculate the tool window size if it is not yet shown (and, therefore, has 0,0,0,0 bounds)
      return
    }

    val toolWindow = source.toolWindow
    val info = getRegisteredMutableInfoOrLogError(toolWindow.id)
    if (info.isFloating) {
      val owner = SwingUtilities.getWindowAncestor(source)
      if (owner != null) {
        info.floatingBounds = owner.bounds
      }
    }
    else if (info.type == ToolWindowType.WINDOWED) {
      val decorator = getWindowedDecorator(toolWindow.id)
      val frame = decorator?.frame
      if (frame == null || !frame.isShowing) {
        return
      }
      info.floatingBounds = getRootBounds(frame as JFrame)
      info.isMaximized = frame.extendedState == Frame.MAXIMIZED_BOTH
    }
    else {
      // docked and sliding windows
      val anchor = info.anchor
      var another: InternalDecorator? = null
      if (source.parent is Splitter) {
        var sizeInSplit = if (anchor.isSplitVertically) source.height.toFloat() else source.width.toFloat()
        val splitter = source.parent as Splitter
        if (splitter.secondComponent === source) {
          sizeInSplit += splitter.dividerWidth.toFloat()
          another = splitter.firstComponent as InternalDecorator
        }
        else {
          another = splitter.secondComponent as InternalDecorator
        }
        if (anchor.isSplitVertically) {
          info.sideWeight = sizeInSplit / splitter.height
        }
        else {
          info.sideWeight = sizeInSplit / splitter.width
        }
      }
      var paneWeight = if (anchor.isHorizontal) source.height.toFloat() / toolWindowPane!!.myLayeredPane.height else source.width.toFloat() / toolWindowPane!!.myLayeredPane.width
      info.weight = paneWeight
      if (another != null && anchor.isSplitVertically) {
        paneWeight = if (anchor.isHorizontal) another.height.toFloat() / toolWindowPane!!.myLayeredPane.height else another.width.toFloat() / toolWindowPane!!.myLayeredPane.width
        getRegisteredMutableInfoOrLogError(another.toolWindow.id).weight = paneWeight
      }
    }
  }

  override fun fallbackToEditor() = activeStack.isEmpty

  private fun focusToolWindowByDefault(toIgnore: ToolWindowEntry?) {
    var toFocus: ToolWindowEntry? = null
    for (each in activeStack.stack) {
      if (toIgnore == each) {
        continue
      }
      if (each.readOnlyWindowInfo.isVisible) {
        toFocus = each
        break
      }
    }

    if (toFocus == null) {
      for (each in activeStack.persistentStack) {
        if (toIgnore == each) {
          continue
        }
        if (each.readOnlyWindowInfo.isVisible) {
          toFocus = each
          break
        }
      }
    }

    if (toFocus != null && !ApplicationManager.getApplication().isDisposed) {
      activateToolWindow(toFocus, false, true)
    }
  }

  /**
   * Delegate method for compatibility with older versions of IDEA
   */
  fun requestFocus(c: Component, forced: Boolean): ActionCallback {
    return IdeFocusManager.getInstance(project).requestFocus(c, forced)
  }

  fun doWhenFocusSettlesDown(runnable: Runnable) {
    IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(runnable)
  }

  fun setShowStripeButton(id: String, visibleOnPanel: Boolean) {
    val info = getRegisteredMutableInfoOrLogError(id)
    if (visibleOnPanel == info.isShowStripeButton) {
      return
    }

    info.isShowStripeButton = visibleOnPanel
    val commands = mutableListOf<Runnable>()
    appendApplyWindowInfoCmd(info, idToEntry.get(id)!!, commands)
    execute(commands)
  }

  fun isShowStripeButton(id: String): Boolean {
    if (!isToolWindowRegistered(id)) {
      return false
    }

    val info = layout.getInfo(id)
    return info == null || info.isShowStripeButton
  }

  internal class InitToolWindowsActivity : StartupActivity {
    override fun runActivity(project: Project) {
      val app = ApplicationManager.getApplication()
      if (app.isUnitTestMode || app.isHeadlessEnvironment) {
        return
      }

      (getInstance(project) as ToolWindowManagerImpl).beforeProjectOpened()
    }
  }

  private fun checkInvariants(additionalMessage: String) {
    if (!ApplicationManager.getApplication().isEAP && !ApplicationManager.getApplication().isInternal) {
      return
    }

    val violations = mutableListOf<String>()
    idToEntry.forEach { (id, entry) ->
      val info = layout.getInfo(id) ?: return@forEach
      if (info.isVisible) {
        if (info.isFloating) {
          if (entry.floatingDecorator == null) {
            violations.add("Floating window has no decorator: $id")
          }
        }
        else if (info.type == ToolWindowType.WINDOWED) {
          if (entry.windowedDecorator == null) {
            violations.add("Windowed window has no decorator: $id")
          }
        }
      }
    }
    if (violations.isNotEmpty()) {
      LOG.error("Invariants failed: \n${java.lang.String.join("\n", violations)}\nContext: $additionalMessage")
    }
  }
}

private enum class KeyState {
  WAITING, PRESSED, RELEASED, HOLD
}

private fun focusDefaultElementInSelectedEditor() {
  EditorsSplitters.findDefaultComponentInSplittersIfPresent { obj: JComponent -> obj.requestFocus() }
}

private fun areAllModifiersPressed(@JdkConstants.InputEventMask modifiers: Int, @JdkConstants.InputEventMask mask: Int): Boolean {
  return (modifiers xor mask) == 0
}

@JdkConstants.InputEventMask
private fun keyCodeToInputMask(code: Int): Int {
  var mask = 0
  if (code == KeyEvent.VK_SHIFT) {
    mask = InputEvent.SHIFT_MASK
  }
  if (code == KeyEvent.VK_CONTROL) {
    mask = InputEvent.CTRL_MASK
  }
  if (code == KeyEvent.VK_META) {
    mask = InputEvent.META_MASK
  }
  if (code == KeyEvent.VK_ALT) {
    mask = InputEvent.ALT_MASK
  }
  return mask
}

// We should filter out 'mixed' mask like InputEvent.META_MASK | InputEvent.META_DOWN_MASK
@get:JdkConstants.InputEventMask
private val activateToolWindowVKsMask: Int
  get() {
    if (!LoadingState.COMPONENTS_LOADED.isOccurred) {
      return 0
    }

    val keymap = KeymapManager.getInstance().activeKeymap
    val baseShortcut = keymap.getShortcuts("ActivateProjectToolWindow")
    var baseModifiers = if (SystemInfo.isMac) InputEvent.META_MASK else InputEvent.ALT_MASK
    for (each in baseShortcut) {
      if (each is KeyboardShortcut) {
        val keyStroke = each.firstKeyStroke
        baseModifiers = keyStroke.modifiers
        if (baseModifiers > 0) {
          break
        }
      }
    }
    // We should filter out 'mixed' mask like InputEvent.META_MASK | InputEvent.META_DOWN_MASK
    return baseModifiers and (InputEvent.SHIFT_MASK or InputEvent.CTRL_MASK or InputEvent.META_MASK or InputEvent.ALT_MASK)
  }

private fun isToHideOnDeactivation(info: WindowInfoImpl): Boolean {
  return if (info.isFloating || info.type == ToolWindowType.WINDOWED) false else info.isAutoHide || info.isSliding
}

private val isStackEnabled: Boolean
  get() = Registry.`is`("ide.enable.toolwindow.stack")

private fun getRootBounds(frame: JFrame): Rectangle {
  val rootPane = frame.rootPane
  val bounds = rootPane.bounds
  bounds.setLocation(frame.x + rootPane.x, frame.y + rootPane.y)
  return bounds
}

private const val EDITOR_ELEMENT = "editor"
private const val ACTIVE_ATTR_VALUE = "active"
private const val FRAME_ELEMENT = "frame"
private const val X_ATTR = "x"
private const val Y_ATTR = "y"
private const val WIDTH_ATTR = "width"
private const val HEIGHT_ATTR = "height"
private const val EXTENDED_STATE_ATTR = "extended-state"
private const val LAYOUT_TO_RESTORE = "layout-to-restore"

internal enum class ToolWindowProperty {
  TITLE, ICON, AVAILABLE, STRIPE_TITLE
}