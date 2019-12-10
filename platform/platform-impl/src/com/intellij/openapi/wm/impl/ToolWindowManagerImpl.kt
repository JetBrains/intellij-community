// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.runActivity
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.actions.MaximizeActiveDialogAction
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ToolWindowCollector
import com.intellij.notification.impl.NotificationsManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
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
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.commands.ApplyWindowInfoCmd
import com.intellij.openapi.wm.impl.commands.FinalizableCommand
import com.intellij.openapi.wm.impl.commands.InvokeLaterCmd
import com.intellij.openapi.wm.impl.commands.RequestFocusInToolWindowCmd
import com.intellij.ui.BalloonImpl
import com.intellij.ui.ColorUtil
import com.intellij.ui.ComponentUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.*
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.PositionTracker
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import org.intellij.lang.annotations.JdkConstants
import org.jdom.Element
import org.jetbrains.annotations.TestOnly
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeListener
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BooleanSupplier
import java.util.function.Predicate
import java.util.function.Supplier
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
  private var layout: DesktopLayout
  private val idToEntry: MutableMap<String, ToolWindowEntry> = HashMap()
  private val internalDecoratorListener: InternalDecoratorListener = MyInternalDecoratorListener()
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

  fun isToolWindowRegistered(id: String): Boolean {
    return layout.isToolWindowRegistered(id)
  }

  private val commandProcessor = CommandProcessor()

  init {
    if (project.isDefault) {
      waiterForSecondPress = null
      layout = DesktopLayout()
    }
    else {
      layout = DesktopLayout()
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
            val info = manager.layout.getInfo(toolWindowId, true) ?: return@process
            val commands = mutableListOf<FinalizableCommand>()
            manager.deactivateToolWindowImpl(info, shouldHide = true, commands = commands)
            // notify clients that toolwindow is deactivated
            manager.execute(commands, true)
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
        for (info in layout.infos) {
          if (info.isVisible) {
            (getToolWindow(info.id ?: continue) as? ToolWindowImpl)?.decorator?.repaint()
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
  fun init() {
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
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(object : ExpirableRunnable.ForProject(project) {
          override fun run() {
            if (FileEditorManager.getInstance(project).hasOpenFiles()) {
              focusToolWindowByDefault(null)
            }
          }
        })
      }
    })

    val frameHelper = WindowManagerEx.getInstanceEx().allocateFrame(project)
    frame = frameHelper
    toolWindowPane = ToolWindowsPane(frameHelper.frame, this, project)

    frameHelper.rootPane!!.setToolWindowsPane(toolWindowPane)

    toolWindowPane!!.putClientProperty(UIUtil.NOT_IN_HIERARCHY_COMPONENTS, Iterable {
      val infos = layout.infos
      val result = ArrayList<JComponent>(infos.size)
      for (info in infos) {
        val decorator = getInternalDecorator(info.id!!)
        if (decorator.parent == null) {
          result.add(decorator)
        }
      }
      result.iterator()
    })
  }

  private fun beforeProjectOpened() {
    LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread)

    val list = mutableListOf<FinalizableCommand>()
    runActivity("toolwindow factory loading") {
      ToolWindowEP.EP_NAME.forEachExtensionSafe { bean ->
        val condition = bean.condition
        // compute outside of EDT
        if (condition != null && !condition.value(project)) {
          return@forEachExtensionSafe
        }

        // compute outside of EDT
        val toolWindowFactory = bean.toolWindowFactory

        list.add(object : FinalizableCommand(null) {
          override fun run() {
            try {
              doInitToolWindow(bean, toolWindowFactory)
            }
            catch (e: ProcessCanceledException) {
              throw e
            }
            catch (t: Throwable) {
              LOG.error("failed to init toolwindow ${bean.factoryClass}", t)
            }
          }
        })
      }
    }

    // must be executed in EDT
    ApplicationManager.getApplication().invokeLater(Runnable {
      pendingSetLayoutTask.getAndSet(null)?.run()

      init()

      ApplicationManager.getApplication().invokeLater(Runnable {
        runActivity("toolwindow creating") {
          appendUpdateRootPane(list)
          val editorComponent = FileEditorManagerEx.getInstanceEx(project).component
          editorComponent.isFocusable = false
          appendSetEditorComponent(editorComponent, list)

          execute(list)
          commandProcessor.flush()
        }

        service<ToolWindowManagerAppLevelHelper>()
      }, project.disposed)

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

  override fun initToolWindow(bean: ToolWindowEP) {
    val condition = bean.condition
    if (condition != null && !condition.value(project)) {
      return
    }

    doInitToolWindow(bean, bean.toolWindowFactory)
  }

  private fun doInitToolWindow(bean: ToolWindowEP, factory: ToolWindowFactory) {
    val before = layout.getInfo(bean.id, false)
    val visible = before != null && before.isVisible
    val label = createInitializingLabel()
    val toolWindowAnchor = ToolWindowAnchor.fromText(bean.anchor)
    val window = registerToolWindow(RegisterToolWindowTask(id = bean.id, component = label, anchor = toolWindowAnchor,
                                                           sideTool = false,
                                                           canCloseContent = bean.canCloseContents,
                                                           canWorkInDumbMode = DumbService.isDumbAware(factory),
                                                           shouldBeAvailable = factory.shouldBeAvailable(project)))
    window.setContentFactory(factory)
    if (bean.icon != null && window.icon == null) {
      var icon = IconLoader.findIcon(bean.icon, factory.javaClass)
      if (icon == null) {
        try {
          icon = IconLoader.getIcon(bean.icon)
        }
        catch (ignored: Exception) {
          icon = EmptyIcon.ICON_13
        }
      }
      window.icon = icon!!
    }

    val info = getRegisteredInfoOrLogError(bean.id)
    if (!info.isSplit && bean.secondary && !info.isWasRead) {
      window.setSplitMode(true, null)
    }

    if (visible) {
      window.ensureContentInitialized()
    }
    else {
      UiNotifyConnector.doWhenFirstShown(label, object : Activatable {
        override fun showNotify() {
          ApplicationManager.getApplication().invokeLater(Runnable {
            if (!window.isDisposed) {
              window.ensureContentInitialized()
            }
          }, project.disposed)
        }
      })
    }
  }

  fun projectClosed() {
    if (frame == null) {
      return
    }

    frame!!.releaseFrame()
    val commands = mutableListOf<FinalizableCommand>()
    appendUpdateRootPane(commands)
    // hide all tool windows
    for (info in layout.infos) {
      deactivateToolWindowImpl(info, true, commands)
    }
    appendSetEditorComponent(null, commands)
    // do not notify - project is disposed
    execute(commands,  /* isFireStateChangedEvent */false)
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

  fun execute(commandList: List<FinalizableCommand>) {
    execute(commandList, true)
  }

  /**
   * This is helper method. It delegated its functionality to the WindowManager.
   * Before delegating it fires state changed.
   */
  private fun execute(commands: List<FinalizableCommand>, isFireStateChangedEvent: Boolean) {
    if (commands.isEmpty()) {
      return
    }

    if (isFireStateChangedEvent && commands.any { it.willChangeState() }) {
      fireStateChanged()
    }

    commandProcessor.execute(commands) { project.isDisposed }
  }

  override fun activateEditorComponent() {
    focusDefaultElementInSelectedEditor()
  }

  private fun deactivateWindows(idToIgnore: String, commands: MutableList<FinalizableCommand>) {
    for (info in layout.infos) {
      if (idToIgnore != info.id) {
        deactivateToolWindowImpl(info, isToHideOnDeactivation(info), commands)
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
  private fun showAndActivate(id: String,
                              dirtyMode: Boolean,
                              commands: MutableList<FinalizableCommand>,
                              autoFocusContents: Boolean) {
    val entry = idToEntry.get(id) ?: return
    if (!entry.internalDecorator.toolWindow.isAvailable) {
      return
    }

    // show activated
    val info = getRegisteredInfoOrLogError(id)
    var toApplyInfo = false
    if (!info.isActive) {
      info.isActive = true
      toApplyInfo = true
    }

    showToolWindowImpl(id, dirtyMode, commands)
    // activate
    if (toApplyInfo) {
      appendApplyWindowInfoCmd(info, entry, commands)
      activeStack.push(id)
    }

    if (autoFocusContents && ApplicationManager.getApplication().isActive) {
      appendRequestFocusInToolWindowCmd(id, commands)
    }
  }

  fun activateToolWindow(id: String, forced: Boolean, autoFocusContents: Boolean) {
    if (LOG.isDebugEnabled) {
      LOG.debug("enter: activateToolWindow($id)")
    }

    ApplicationManager.getApplication().assertIsDispatchThread()
    getRegisteredInfoOrLogError(id)
    val commandList = mutableListOf<FinalizableCommand>()
    activateToolWindowImpl(id, commandList, forced, autoFocusContents)
    execute(commandList)
  }

  private fun activateToolWindowImpl(id: String,
                                     commandList: MutableList<FinalizableCommand>,
                                     forced: Boolean,
                                     autoFocusContents: Boolean) {
    var effectiveAutoFocusContents = autoFocusContents
    ToolWindowCollector.recordActivation(id, layout.getInfo(id, true))
    effectiveAutoFocusContents = effectiveAutoFocusContents and forced
    if (LOG.isDebugEnabled) {
      LOG.debug("enter: activateToolWindowImpl($id)")
    }
    if (!getToolWindow(id)!!.isAvailable) {
      // Tool window can be "logically" active but not focused. For example,
      // when the user switched to another application. So we just need to bring
      // tool window's window to front.
      val decorator = getInternalDecorator(id)
      if (!decorator.hasFocus() && effectiveAutoFocusContents) {
        appendRequestFocusInToolWindowCmd(id, commandList)
      }
      return
    }
    deactivateWindows(id, commandList)
    showAndActivate(id, false, commandList, effectiveAutoFocusContents)
  }

  private fun getRegisteredInfoOrLogError(id: String): WindowInfoImpl {
    val info = layout.getInfo(id, false) ?: throw IllegalThreadStateException("window with id=\"$id\" is unknown")
    if (!info.isRegistered) {
      LOG.error("window with id=\"$id\" isn't registered")
    }
    return info
  }

  /**
   * Helper method. It deactivates (and hides) window with specified `id`.
   */
  private fun deactivateToolWindowImpl(info: WindowInfoImpl, shouldHide: Boolean, commands: MutableList<FinalizableCommand>) {
    if (LOG.isDebugEnabled) {
      LOG.debug("enter: deactivateToolWindowImpl(${info.id},$shouldHide)")
    }
    if (shouldHide) {
      appendRemoveDecoratorCommand(info, false, commands)
    }
    info.isActive = false
    appendApplyWindowInfoCmd(info, idToEntry.get(info.id)!!, commands)
    checkInvariants("Info: $info; shouldHide: $shouldHide")
  }

  override val toolWindowIds: Array<String>
    get() {
      val infos = layout.infos
      val ids = ArrayUtil.newStringArray(infos.size)
      for (i in infos.indices) {
        ids[i] = infos[i].id
      }
      return ids
    }

  override val activeToolWindowId: String?
    get() {
      ApplicationManager.getApplication().assertIsDispatchThread()
      return layout.activeId
    }

  override fun getLastActiveToolWindowId(): String? {
    return getLastActiveToolWindowId(null)
  }

  override fun getLastActiveToolWindowId(condition: Condition<in JComponent>?): String? {
    ApplicationManager.getApplication().assertIsDispatchThread()
    var lastActiveToolWindowId: String? = null
    for (i in 0 until activeStack.persistentSize) {
      val id = activeStack.peekPersistent(i)
      val toolWindow = getToolWindow(id)
      LOG.assertTrue(toolWindow != null)
      if (toolWindow!!.isAvailable) {
        if (condition == null || condition.value(toolWindow.component)) {
          lastActiveToolWindowId = id
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
  private fun getWindowedDecorator(id: String): WindowedDecorator? {
    return idToEntry.get(id)?.windowedDecorator
  }

  /**
   * @return internal decorator for the tool window with specified `ID`.
   */
  fun getInternalDecorator(id: String) = idToEntry.get(id)!!.internalDecorator

  /**
   * @return tool button for the window with specified `ID`.
   */
  fun getStripeButton(id: String) = idToEntry.get(id)!!.stripeButton

  override fun getIdsOn(anchor: ToolWindowAnchor) = layout.getVisibleIdsOn(anchor, this)

  override fun getLocationIcon(id: String, fallbackIcon: Icon): Icon {
    val window = getToolWindow(id)
    val info = layout.getInfo(id, false)
    if (window == null && info == null) {
      return fallbackIcon
    }

    val type = if (window == null) info!!.type else window.type
    if (type == ToolWindowType.FLOATING || type == ToolWindowType.WINDOWED) {
      return AllIcons.Actions.MoveToWindow
    }

    val anchor = if (window == null) info!!.anchor else window.anchor
    val splitMode = window?.isSplitMode ?: info!!.isSplit
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

  // cannot be ToolWindowEx because of backward compatibility
  override fun getToolWindow(id: String): ToolWindow? {
    return idToEntry.get(id)?.internalDecorator?.toolWindow
  }

  fun showToolWindow(id: String) {
    if (LOG.isDebugEnabled) {
      LOG.debug("enter: showToolWindow($id)")
    }
    ApplicationManager.getApplication().assertIsDispatchThread()
    val commandList = mutableListOf<FinalizableCommand>()
    showToolWindowImpl(id, false, commandList)
    execute(commandList)
  }

  override fun hideToolWindow(id: String, hideSide: Boolean) {
    hideToolWindow(id, hideSide, true)
  }

  fun hideToolWindow(id: String, hideSide: Boolean, moveFocus: Boolean) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val info = getRegisteredInfoOrLogError(id)
    val commandList = mutableListOf<FinalizableCommand>()
    val wasActive = info.isActive
    // hide and deactivate
    deactivateToolWindowImpl(info, true, commandList)
    if (hideSide && !info.isFloating && !info.isWindowed) {
      val ids = layout.getVisibleIdsOn(info.anchor, this)
      for (each in ids) {
        activeStack.remove(each, true)
      }
      if (isStackEnabled) {
        while (!sideStack.isEmpty(info.anchor)) {
          sideStack.pop(info.anchor)
        }
      }
      for (eachInfo in layout.infos) {
        if (eachInfo.isVisible && eachInfo.anchor == info.anchor) {
          deactivateToolWindowImpl(eachInfo, true, commandList)
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
          val currentInfo = getRegisteredInfoOrLogError(storedInfo.id!!)
          // SideStack contains copies of real WindowInfos. It means that
          // these stored infos can be invalid. The following loop removes invalid WindowInfos.
          if (storedInfo.anchor == currentInfo.anchor && storedInfo.type == currentInfo.type && storedInfo.isAutoHide == currentInfo.isAutoHide) {
            info2 = storedInfo
            break
          }
        }
        if (info2 != null) {
          showToolWindowImpl(info2.id!!, false, commandList)
        }
      }
      // If we hide currently active tool window then we should activate the previous
      // one which is located in the tool window stack.
      // Activate another tool window if no active tool window exists and
      // window stack is enabled.
      activeStack.remove(id, false) // hidden window should be at the top of stack
      if (wasActive && moveFocus && !activeStack.isEmpty) {
        val toBeActivatedId = activeStack.pop()
        if (getRegisteredInfoOrLogError(toBeActivatedId).isVisible || isStackEnabled) {
          activateToolWindowImpl(toBeActivatedId, commandList, false, true)
        }
        else {
          focusToolWindowByDefault(id)
        }
      }
    }
    execute(commandList)
  }

  /**
   * @param dirtyMode if `true` then all UI operations are performed in dirty mode.
   */
  private fun showToolWindowImpl(id: String, dirtyMode: Boolean, commands: MutableList<FinalizableCommand>) {
    val toBeShownInfo = getRegisteredInfoOrLogError(id)
    val window = getToolWindow(id)
    if (window != null && toBeShownInfo.isWindowed) {
      UIUtil.toFront(UIUtil.getWindow(window.component))
    }

    if (toBeShownInfo.isVisible || (window == null) || !window.isAvailable) {
      return
    }

    toBeShownInfo.isVisible = true
    val entry = idToEntry.get(id)
    if (toBeShownInfo.isFloating) {
      commands.add(AddFloatingDecoratorCmd((entry)!!, toBeShownInfo))
    }
    else if (toBeShownInfo.isWindowed) {
      commands.add(AddWindowedDecoratorCmd((entry)!!, toBeShownInfo))
    }
    else {
      // docked and sliding windows
      // If there is tool window on the same side then we have to hide it, i.e.
      // clear place for tool window to be shown.
      //
      // We store WindowInfo of hidden tool window in the SideStack (if the tool window
      // is docked and not auto-hide one). Therefore it's possible to restore the
      // hidden tool window when showing tool window will be closed.
      for (info in layout.infos) {
        if (id == info.id) {
          continue
        }

        if (info.isVisible && (info.type == toBeShownInfo.type) && (info.anchor == toBeShownInfo.anchor) && (info.isSplit == toBeShownInfo.isSplit)) {
          // hide and deactivate tool window
          info.isVisible = false
          appendRemoveDecoratorCmd(info.id!!, false, commands)
          if (info.isActive) {
            info.isActive = false
          }
          appendApplyWindowInfoCmd(info, idToEntry.get(info.id!!) ?: continue, commands)
          // store WindowInfo into the SideStack
          if (isStackEnabled) {
            if (info.isDocked && !info.isAutoHide) {
              sideStack.push(info)
            }
          }
        }
      }

      appendAddDecoratorCmd(entry!!.internalDecorator, toBeShownInfo, dirtyMode, commands)
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
    init()

    if (LOG.isDebugEnabled) {
      LOG.debug("enter: installToolWindow($task)")
    }

    ApplicationManager.getApplication().assertIsDispatchThread()
    if (idToEntry.containsKey(task.id)) {
      throw IllegalArgumentException("window with id=\"${task.id}\" is already registered")
    }

    val info = layout.register(task)
    val wasActive = info.isActive
    val wasVisible = info.isVisible
    info.isActive = false
    info.isVisible = false

    val disposable = Disposer.newDisposable(task.id)
    Disposer.register(project, disposable)

    val toolWindow = ToolWindowImpl(this, task.id, task.canCloseContent, task.component, disposable)
    toolWindow.setAvailable(task.shouldBeAvailable, null)
    ActivateToolWindowAction.ensureToolWindowActionRegistered(toolWindow)

    // create decorator
    val decorator = InternalDecorator(project, info.copy(), toolWindow, task.canWorkInDumbMode, disposable, internalDecoratorListener)

    // create and show tool button
    val button = StripeButton(decorator, (toolWindowPane)!!)
    val commands = mutableListOf<FinalizableCommand>()
    appendAddButtonCmd(button, info, commands)

    idToEntry.put(task.id, ToolWindowEntry(button, decorator, ToolWindowFocusWatcher(toolWindow), disposable))

    // If preloaded info is visible or active then we have to show/activate the installed
    // tool window. This step has sense only for windows which are not in the auto hide
    // mode. But if tool window was active but its mode doesn't allow to activate it again
    // (for example, tool window is in auto hide mode) then we just activate editor component.
    if (!info.isAutoHide && (info.isDocked || info.isFloating)) {
      if (wasActive) {
        activateToolWindowImpl(info.id!!, commands, true, true)
      }
      else if (wasVisible) {
        showToolWindowImpl(info.id!!, false, commands)
      }
    }
    execute(commands)
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowRegistered(task.id)
    return toolWindow
  }

  @Suppress("OverridingDeprecatedMember")
  override fun unregisterToolWindow(id: String) {
    doUnregisterToolWindow(id)
  }

  private fun doUnregisterToolWindow(id: String) {
    if (LOG.isDebugEnabled) {
      LOG.debug("enter: unregisterToolWindow($id)")
    }

    ApplicationManager.getApplication().assertIsDispatchThread()

    val entry = idToEntry.remove(id) ?: return
    val toolWindow = entry.internalDecorator.toolWindow ?: return

    val info = layout.getInfo(id, false)
    if (info == null || !info.isRegistered) {
      return
    }

    // remove decorator and tool button from the screen
    val commands = mutableListOf<FinalizableCommand>()
    appendRemoveDecoratorCommand(info, false, commands)
    // Save recent appearance of tool window
    layout.unregister(id)
    activeStack.remove(id, true)
    if (isStackEnabled) {
      sideStack.remove(id)
    }
    appendRemoveButtonCmd(id, entry, info, commands)
    appendApplyWindowInfoCmd(info, entry, commands)
    execute(commands, false)

    if (!project.isDisposed) {
      project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowUnregistered(id, (toolWindow))
    }

    Disposer.dispose(entry.disposable)
    entry.watcher.deinstall()
  }

  private fun appendRemoveDecoratorCommand(info: WindowInfoImpl, dirtyMode: Boolean, commands: MutableList<FinalizableCommand>) {
    if (!info.isVisible) {
      return
    }

    info.isVisible = false
    when {
      info.isFloating -> commands.add(RemoveFloatingDecoratorCmd(info))
      info.isWindowed -> commands.add(RemoveWindowedDecoratorCmd(info))
      // docked and sliding windows
      else -> appendRemoveDecoratorCmd(info.id!!, dirtyMode, commands)
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
    val currentInfos = this.layout.infos
    if (currentInfos.isEmpty()) {
      this.layout = layout
      return
    }

    val commands = mutableListOf<FinalizableCommand>()
    for (currentInfo in currentInfos) {
      val info = layout.getInfo(currentInfo.id!!, false)
      if (currentInfo.isVisible && (info == null || !info.isVisible)) {
        deactivateToolWindowImpl(currentInfo, true, commands)
      }
    }

    // change anchor of tool windows
    for (currentInfo in currentInfos) {
      val currentId = currentInfo.id ?: continue
      val info = layout.getInfo(currentId, false) ?: continue
      if (currentInfo.anchor != info.anchor || currentInfo.order != info.order) {
        setToolWindowAnchorImpl(currentId, info.anchor, info.order, commands)
      }
    }

    // change types of tool windows
    for (currentInfo in currentInfos) {
      val info = layout.getInfo(currentInfo.id!!, false) ?: continue
      if (currentInfo.type != info.type) {
        setToolWindowTypeImpl((currentInfo.id)!!, info.type, commands)
      }
    }
    // change other properties
    for (currentInfo in currentInfos) {
      val info = layout.getInfo(currentInfo.id!!, false) ?: continue
      copyWindowOptions(info, commands)
    }
    // restore visibility
    for (currentInfo in currentInfos) {
      val info = layout.getInfo(currentInfo.id!!, false) ?: continue
      if (info.isVisible) {
        showToolWindowImpl((currentInfo.id)!!, false, commands)
      }
    }
    execute(commands)
    checkInvariants("")
    this.layout = layout
  }

  override fun invokeLater(runnable: Runnable) {
    execute(listOf(InvokeLaterCmd(runnable, commandProcessor)))
  }

  override val focusManager: IdeFocusManager
    get() = IdeFocusManager.getInstance(project)!!

  override fun canShowNotification(toolWindowId: String): Boolean {
    if (!layout.isToolWindowRegistered(toolWindowId)) {
      return false
    }
    return toolWindowPane!!.getStripeFor(toolWindowId)?.getButtonFor(toolWindowId) != null
  }

  override fun notifyByBalloon(toolWindowId: String, type: MessageType, htmlBody: String) {
    notifyByBalloon(toolWindowId, type, htmlBody, null, null)
  }

  override fun notifyByBalloon(toolWindowId: String, type: MessageType, htmlBody: String, icon: Icon?, listener: HyperlinkListener?) {
    getRegisteredInfoOrLogError(toolWindowId)
    val entry = idToEntry.get(toolWindowId)
    val existing = entry!!.balloon
    if (existing != null) {
      Disposer.dispose(existing)
    }

    val stripe = toolWindowPane!!.getStripeFor(toolWindowId) ?: return
    val window = getInternalDecorator(toolWindowId).toolWindow
    if (!window.isAvailable) {
      window.isPlaceholderMode = true
      stripe.updatePresentation()
      stripe.revalidate()
      stripe.repaint()
    }

    val anchor = getRegisteredInfoOrLogError(toolWindowId).anchor
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
      window.isPlaceholderMode = false
      stripe.updatePresentation()
      stripe.revalidate()
      stripe.repaint()
      entry.balloon = null
    })
    Disposer.register(entry.disposable, balloon)
    execute(listOf(object : FinalizableCommand(null) {
      override fun run() {
        val button = stripe.getButtonFor(toolWindowId)
        LOG.assertTrue(button != null, ("Button was not found, popup won't be shown. Toolwindow id: $toolWindowId, message: $htmlBody, message type: $type"))
        if (button == null) {
          return
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
      }
    }))
  }

  override fun getToolWindowBalloon(id: String) = idToEntry.get(id)?.balloon

  override val isEditorComponentActive: Boolean
    get() {
      ApplicationManager.getApplication().assertIsDispatchThread()
      return ComponentUtil.getParentOfType(EditorsSplitters::class.java, focusManager.focusOwner) != null
    }

  fun getToolWindowAnchor(id: String) = getRegisteredInfoOrLogError(id).anchor

  fun setToolWindowAnchor(id: String, anchor: ToolWindowAnchor) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    setToolWindowAnchor(id, anchor, -1)
  }

  // used by Rider
  @Suppress("MemberVisibilityCanBePrivate")
  fun setToolWindowAnchor(id: String, anchor: ToolWindowAnchor, order: Int) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val commandList = mutableListOf<FinalizableCommand>()
    setToolWindowAnchorImpl(id, anchor, order, commandList)
    execute(commandList)
  }

  private fun setToolWindowAnchorImpl(id: String, anchor: ToolWindowAnchor, order: Int, commands: MutableList<FinalizableCommand>) {
    val info = getRegisteredInfoOrLogError(id)
    if (anchor == info.anchor && order == info.order) {
      return
    }

    fun appendRemoveButtonCmdIfCan() {
      idToEntry.get(id)?.let {
        appendRemoveButtonCmd(id, it, info, commands)
      }
    }

    // if tool window isn't visible or only order number is changed then just remove/add stripe button
    if (!info.isVisible || anchor == info.anchor || info.isFloating || info.isWindowed) {
      appendRemoveButtonCmdIfCan()
      layout.setAnchor(id, anchor, order)
      // update infos for all window. Actually we have to update only infos affected by
      // setAnchor method
      for (info1 in layout.infos) {
        appendApplyWindowInfoCmd(info1, idToEntry.get(info1.id!!) ?: continue, commands)
      }
      appendAddButtonCmd(getStripeButton(id), info, commands)
    }
    else {
      // for docked and sliding windows we have to move buttons and window's decorators
      info.isVisible = false
      appendRemoveDecoratorCmd(id, false, commands)
      appendRemoveButtonCmdIfCan()
      layout.setAnchor(id, anchor, order)
      // update infos for all window. Actually we have to update only infos affected by
      // setAnchor method
      for (info1 in layout.infos) {
        appendApplyWindowInfoCmd(info1, idToEntry.get(info1.id!!) ?: continue, commands)
      }
      appendAddButtonCmd(getStripeButton(id), info, commands)
      showToolWindowImpl(id, false, commands)
      if (info.isActive) {
        appendRequestFocusInToolWindowCmd(id, commands)
      }
    }
  }

  fun isSplitMode(id: String): Boolean {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return getRegisteredInfoOrLogError(id).isSplit
  }

  fun getContentUiType(id: String): ToolWindowContentUiType {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return getRegisteredInfoOrLogError(id).contentUiType
  }

  fun setSideTool(id: String, isSide: Boolean) {
    val commandList = mutableListOf<FinalizableCommand>()
    setSplitModeImpl(id, isSide, commandList)
    execute(commandList)
  }

  fun setContentUiType(id: String, type: ToolWindowContentUiType) {
    val info = getRegisteredInfoOrLogError(id)
    info.contentUiType = type
    val commands = mutableListOf<FinalizableCommand>()
    appendApplyWindowInfoCmd(info, idToEntry.get(info.id!!)!!, commands)
    execute(commands)
  }

  fun setSideToolAndAnchor(id: String, anchor: ToolWindowAnchor, order: Int, isSide: Boolean) {
    hideToolWindow(id, false)
    layout.setSplitMode(id, isSide)
    setToolWindowAnchor(id, anchor, order)
    activateToolWindow(id, false, false)
  }

  private fun setSplitModeImpl(id: String, isSplit: Boolean, commands: MutableList<FinalizableCommand>) {
    val info = getRegisteredInfoOrLogError(id)
    if (isSplit == info.isSplit) {
      return
    }

    layout.setSplitMode(id, isSplit)
    val wasActive = info.isActive
    val wasVisible = info.isVisible
    // we should hide the window and show it in a 'new place' to automatically hide possible window that is already located in a 'new place'
    if (wasActive || wasVisible) {
      hideToolWindow(id, false)
    }
    for (otherInfo in layout.infos) {
      appendApplyWindowInfoCmd(otherInfo, idToEntry.get(otherInfo.id!!) ?: continue, commands)
    }
    if (wasVisible || wasActive) {
      showToolWindowImpl(id, true, commands)
    }
    if (wasActive) {
      activateToolWindowImpl(id, commands, true, true)
    }
    commands.add(toolWindowPane!!.createUpdateButtonPositionCmd(Supplier { idToEntry.get(id)?.stripeButton }, commandProcessor))
  }

  fun getToolWindowInternalType(id: String): ToolWindowType {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return getRegisteredInfoOrLogError(id).internalType
  }

  fun getToolWindowType(id: String) = getRegisteredInfoOrLogError(id).type

  protected open fun fireStateChanged() {
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).stateChanged()
  }

  fun isToolWindowActive(id: String): Boolean {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return layout.getInfo(id, false)?.isActive ?: false
  }

  fun isToolWindowAutoHide(id: String): Boolean {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return getRegisteredInfoOrLogError(id).isAutoHide
  }

  fun isToolWindowVisible(id: String): Boolean {
    return getRegisteredInfoOrLogError(id).isVisible
  }

  fun setToolWindowAutoHide(id: String, autoHide: Boolean) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val commandList = mutableListOf<FinalizableCommand>()
    setToolWindowAutoHideImpl(id, autoHide, commandList)
    execute(commandList)
  }

  private fun setToolWindowAutoHideImpl(id: String, autoHide: Boolean, commands: MutableList<FinalizableCommand>) {
    val info = getRegisteredInfoOrLogError(id)
    if (info.isAutoHide == autoHide) {
      return
    }

    info.isAutoHide = autoHide
    appendApplyWindowInfoCmd(info, idToEntry.get(info.id!!)!!, commands)
    if (info.isVisible) {
      deactivateWindows(id, commands)
      showAndActivate(id, false, commands, true)
    }
  }

  private fun copyWindowOptions(origin: WindowInfoImpl, commands: MutableList<FinalizableCommand>) {
    val id = origin.id!!
    val info = getRegisteredInfoOrLogError(id)
    var changed = false
    if (info.isAutoHide != origin.isAutoHide) {
      info.isAutoHide = origin.isAutoHide
      changed = true
    }
    if (info.weight != origin.weight) {
      info.weight = origin.weight
      changed = true
    }
    if (info.sideWeight != origin.sideWeight) {
      info.sideWeight = origin.sideWeight
      changed = true
    }
    if (info.contentUiType !== origin.contentUiType) {
      info.contentUiType = origin.contentUiType
      changed = true
    }

    if (changed) {
      appendApplyWindowInfoCmd(info, idToEntry.get(id)!!, commands)
      if (info.isVisible) {
        deactivateWindows(id, commands)
        showAndActivate(id, false, commands, true)
      }
    }
  }

  fun setToolWindowType(id: String, type: ToolWindowType) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val commandList = mutableListOf<FinalizableCommand>()
    setToolWindowTypeImpl(id, type, commandList)
    execute(commandList)
  }

  private fun setToolWindowTypeImpl(id: String, type: ToolWindowType, commands: MutableList<FinalizableCommand>) {
    val info = getRegisteredInfoOrLogError(id)
    if (info.type == type) {
      return
    }

    val entry = idToEntry.get(id)!!

    if (info.isVisible) {
      val dirtyMode = info.isDocked || info.isSliding
      appendRemoveDecoratorCommand(info, dirtyMode, commands)
      info.type = type
      appendApplyWindowInfoCmd(info, entry, commands)
      deactivateWindows(id, commands)
      showAndActivate(id, dirtyMode, commands, true)
      appendUpdateRootPane(commands)
    }
    else {
      info.type = type
      appendApplyWindowInfoCmd(info, entry, commands)
    }
  }

  private fun appendApplyWindowInfoCmd(info: WindowInfoImpl, entry: ToolWindowEntry, commands: MutableList<FinalizableCommand>) {
    commands.add(ApplyWindowInfoCmd(info, entry.stripeButton, entry.internalDecorator, commandProcessor))
  }

  /**
   * @see ToolWindowsPane.createAddDecoratorCmd
   */
  private fun appendAddDecoratorCmd(decorator: InternalDecorator, info: WindowInfoImpl, dirtyMode: Boolean, commands: MutableList<FinalizableCommand>) {
    commands.add(toolWindowPane!!.createAddDecoratorCmd(decorator, info, dirtyMode, commandProcessor))
  }

  /**
   * @see ToolWindowsPane.createRemoveDecoratorCmd
   */
  private fun appendRemoveDecoratorCmd(id: String,
                                       dirtyMode: Boolean,
                                       commandsList: MutableList<FinalizableCommand>) {
    commandsList.add(toolWindowPane!!.createRemoveDecoratorCmd(id, dirtyMode, commandProcessor))
    commandsList.add(toolWindowPane!!.createTransferFocusCmd(BooleanSupplier { commandsList.any { it is RequestFocusInToolWindowCmd } }, commandProcessor))
  }

  /**
   * @see ToolWindowsPane.createAddButtonCmd
   */
  private fun appendAddButtonCmd(button: StripeButton, info: WindowInfoImpl, commandsList: MutableList<FinalizableCommand>) {
    val comparator = layout.comparator(info.anchor)
    commandsList.add(toolWindowPane!!.createAddButtonCmd(button, info, comparator, commandProcessor))
  }

  /**
   * @see ToolWindowsPane.createAddButtonCmd
   */
  private fun appendRemoveButtonCmd(id: String, toolWindowEntry: ToolWindowEntry, info: WindowInfoImpl, commands: MutableList<FinalizableCommand>) {
    commands.add(toolWindowPane!!.createRemoveButtonCmd(toolWindowEntry.stripeButton, info, id, commandProcessor))
  }

  private fun appendRequestFocusInToolWindowCmd(id: String, commandList: MutableList<FinalizableCommand>) {
    if (!layout.isToolWindowRegistered(id)) {
      return
    }

    val entry = idToEntry.get(id) ?: return
    commandList.add(RequestFocusInToolWindowCmd(entry.internalDecorator.toolWindow, entry.watcher, commandProcessor, project))
  }

  private fun appendSetEditorComponent(component: JComponent?, commands: MutableList<FinalizableCommand>) {
    commands.add(toolWindowPane!!.createSetEditorComponentCmd(component, commandProcessor))
  }

  private fun appendUpdateRootPane(commands: MutableList<FinalizableCommand>) {
    val frame = frame!!
    commands.add(object : FinalizableCommand(commandProcessor) {
      override fun run() {
        try {
          val rootPane = frame.rootPane ?: return
          rootPane.revalidate()
          rootPane.repaint()
        }
        finally {
          finish()
        }
      }
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

    // update size of all open floating windows. See SCR #18439
    for (info in layout.infos) {
      if (info.isVisible) {
        getInternalDecorator(info.id!!).fireResized()
      }
    }

    val element = Element("state")
    // Save frame's bounds
    // [tav] Where we load these bounds? Should we just remove this code? (because we load frame bounds in WindowManagerImpl.allocateFrame)
    // Anyway, we should save bounds in device space to preserve backward compatibility with the IDE-managed HiDPI mode (see JBUI.ScaleType).
    // However, I won't change this code because I can't even test it.
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
    val info = getRegisteredInfoOrLogError(toolWindow.id)
    if (info.isWasRead) {
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
    val info = getRegisteredInfoOrLogError(toolWindow.id)
    if (info.isWasRead) {
      return
    }
    toolWindow.setContentUiType(type, null)
  }

  fun stretchWidth(toolWindow: ToolWindowImpl, value: Int) {
    toolWindowPane!!.stretchWidth(toolWindow, value)
  }

  override fun isMaximized(wnd: ToolWindow) = toolWindowPane!!.isMaximized(wnd)

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

  /**
   * This command creates and shows `FloatingDecorator`.
   */
  private inner class AddFloatingDecoratorCmd(entry: ToolWindowEntry, info: WindowInfoImpl) : FinalizableCommand(commandProcessor) {
    private val floatingDecorator: FloatingDecorator

    override fun run() {
      try {
        @Suppress("DEPRECATION")
        floatingDecorator.show()
      }
      finally {
        finish()
      }
    }

    /**
     * Creates floating decorator for specified internal decorator.
     */
    init {
      val frame = frame!!.frame
      val decorator = entry.internalDecorator
      floatingDecorator = FloatingDecorator(frame, info.copy(), decorator)
      entry.floatingDecorator = floatingDecorator
      val bounds = info.floatingBounds
      if ((bounds != null && bounds.width > 0 && bounds.height > 0 &&
           WindowManager.getInstance().isInsideScreenBounds(bounds.x, bounds.y, bounds.width))) {
        floatingDecorator.bounds = Rectangle(bounds)
      }
      else {
        // place new frame at the center of main frame if there are no floating bounds
        var size = decorator.size
        if (size.width == 0 || size.height == 0) {
          size = decorator.preferredSize
        }
        floatingDecorator.size = size
        floatingDecorator.setLocationRelativeTo(frame)
      }
    }
  }

  /**
   * This command hides and destroys floating decorator for tool window
   * with specified `ID`.
   */
  private inner class RemoveFloatingDecoratorCmd(info: WindowInfoImpl) : FinalizableCommand(commandProcessor) {
    private val floatingDecorator = getFloatingDecorator(info.id!!)

    override fun run() {
      try {
        floatingDecorator!!.dispose()
      }
      finally {
        finish()
      }
    }

    override fun getExpireCondition() = BooleanSupplier { ApplicationManager.getApplication().isDisposed }

    init {
      info.floatingBounds = floatingDecorator!!.bounds
    }
  }

  /**
   * This command creates and shows `WindowedDecorator`.
   */
  private inner class AddWindowedDecoratorCmd(entry: ToolWindowEntry, info: WindowInfoImpl) : FinalizableCommand(commandProcessor) {
    private val windowedDecorator: WindowedDecorator
    private val shouldBeMaximized: Boolean

    override fun run() {
      try {
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
      finally {
        finish()
      }
    }

    /**
     * Creates windowed decorator for specified internal decorator.
     */
    init {
      val decorator = entry.internalDecorator
      windowedDecorator = WindowedDecorator(project, info.copy(), decorator)
      shouldBeMaximized = info.isMaximized
      val window = windowedDecorator.frame
      val bounds = info.floatingBounds
      if ((bounds != null && bounds.width > 0 && (bounds.height > 0) &&
           WindowManager.getInstance().isInsideScreenBounds(bounds.x, bounds.y, bounds.width))) {
        window.bounds = Rectangle(bounds)
      }
      else { // place new frame at the center of main frame if there are no floating bounds
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
   * This command hides and destroys floating decorator for tool window
   * with specified `ID`.
   */
  private inner class RemoveWindowedDecoratorCmd(info: WindowInfoImpl) : FinalizableCommand(commandProcessor) {
    private val windowedDecorator = getWindowedDecorator(info.id!!)

    override fun run() {
      try {
        Disposer.dispose((windowedDecorator)!!)
      }
      finally {
        finish()
      }
    }

    override fun getExpireCondition(): BooleanSupplier {
      return BooleanSupplier { ApplicationManager.getApplication().isDisposed }
    }

    init {
      val entry = idToEntry[info.id]
      if (entry != null) {
        entry.windowedDecorator = null
      }

      val frame = windowedDecorator!!.frame
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
    }
  }

  /**
   * Notifies window manager about focus traversal in tool window
   */
  internal class ToolWindowFocusWatcher(toolWindow: ToolWindowImpl) : FocusWatcher() {
    private val id = toolWindow.id
    private val toolWindow: ToolWindowImpl

    fun deinstall() {
      deinstall(toolWindow.component)
    }

    override fun isFocusedComponentChangeValid(component: Component?, cause: AWTEvent?): Boolean {
      return component != null && toolWindow.toolWindowManager.commandProcessor.commandCount == 0
    }

    override fun focusedComponentChanged(component: Component?, cause: AWTEvent?) {
      if (component == null || toolWindow.toolWindowManager.commandProcessor.commandCount > 0) {
        return
      }

      val info = toolWindow.toolWindowManager.getRegisteredInfoOrLogError(id)
      if (!info.isActive) {
        IdeFocusManager.getInstance(toolWindow.toolWindowManager.project).doWhenFocusSettlesDown(object : EdtRunnable() {
          override fun runEdt() {
            val windowInfo = toolWindow.toolWindowManager.layout.getInfo(id, true)
            if (windowInfo == null || !windowInfo.isVisible) return
            toolWindow.toolWindowManager.activateToolWindow(id, false, false)
          }
        })
      }
    }

    init {
      install(toolWindow.component)
      this.toolWindow = toolWindow
    }
  }

  /**
   * Spies on IdeToolWindow properties and applies them to the window
   * state.
   */
  fun toolWindowPropertyChanged(toolWindow: ToolWindowImpl, propertyName: String?) {
    if (propertyName == ToolWindowEx.PROP_AVAILABLE) {
      val info = getRegisteredInfoOrLogError(toolWindow.id)
      if (!toolWindow.isAvailable && info.isVisible) {
        hideToolWindow(toolWindow.id, false)
      }
    }
    idToEntry.get(toolWindow.id)?.stripeButton?.updatePresentation()
    ActivateToolWindowAction.updateToolWindowActionPresentation(toolWindow)
  }

  /**
   * Translates events from InternalDecorator into ToolWindowManager method invocations.
   */
  private inner class MyInternalDecoratorListener : InternalDecoratorListener {
    override fun anchorChanged(source: InternalDecorator, anchor: ToolWindowAnchor) {
      setToolWindowAnchor(source.toolWindow.id, anchor)
    }

    override fun autoHideChanged(source: InternalDecorator, autoHide: Boolean) {
      setToolWindowAutoHide(source.toolWindow.id, autoHide)
    }

    override fun hidden(source: InternalDecorator) {
      hideToolWindow(source.toolWindow.id, false)
    }

    override fun hiddenSide(source: InternalDecorator) {
      hideToolWindow(source.toolWindow.id, true)
    }

    override fun contentUiTypeChanges(source: InternalDecorator, type: ToolWindowContentUiType) {
      setContentUiType(source.toolWindow.id, type)
    }

    /**
     * Handles event from decorator and modify weight/floating bounds of the
     * tool window depending on decoration type.
     */
    override fun resized(source: InternalDecorator) {
      if (!source.isShowing) {
        // do not recalculate the tool window size if it is not yet shown (and, therefore, has 0,0,0,0 bounds)
        return
      }

      val info = getRegisteredInfoOrLogError(source.toolWindow.id)
      if (info.isFloating) {
        val owner = SwingUtilities.getWindowAncestor(source)
        if (owner != null) {
          info.floatingBounds = owner.bounds
        }
      }
      else if (info.isWindowed) {
        val decorator = getWindowedDecorator(info.id!!)
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
          another.windowInfo.weight = paneWeight
        }
      }
    }

    override fun activated(source: InternalDecorator) {
      activateToolWindow(source.toolWindow.id, true, true)
    }

    override fun typeChanged(source: InternalDecorator, type: ToolWindowType) {
      setToolWindowType(source.toolWindow.id, type)
    }

    override fun sideStatusChanged(source: InternalDecorator, isSideTool: Boolean) {
      setSideTool(source.toolWindow.id, isSideTool)
    }

    override fun visibleStripeButtonChanged(source: InternalDecorator, visible: Boolean) {
      setShowStripeButton(source.toolWindow.id, visible)
    }
  }

  override fun fallbackToEditor() = activeStack.isEmpty

  private fun focusToolWindowByDefault(idToIgnore: String?) {
    var toFocus: String? = null
    for (each in activeStack.stack) {
      if (idToIgnore != null && idToIgnore.equals(each, ignoreCase = true)) continue
      if (getRegisteredInfoOrLogError(each).isVisible) {
        toFocus = each
        break
      }
    }

    if (toFocus == null) {
      for (each: String in activeStack.persistentStack) {
        if (idToIgnore != null && idToIgnore.equals(each, ignoreCase = true)) continue
        if (getRegisteredInfoOrLogError(each).isVisible) {
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
    val info = getRegisteredInfoOrLogError(id)
    if (visibleOnPanel == info.isShowStripeButton) {
      return
    }

    info.isShowStripeButton = visibleOnPanel
    val commands = mutableListOf<FinalizableCommand>()
    appendApplyWindowInfoCmd(info, idToEntry.get(id)!!, commands)
    execute(commands)
  }

  fun isShowStripeButton(id: String): Boolean {
    val info = layout.getInfo(id, true)
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
    for (info in layout.infos) {
      val id = info.id!!
      if (info.isVisible) {
        if (info.isFloating) {
          val entry = idToEntry[id]
          if (entry != null && entry.floatingDecorator == null) {
            violations.add("Floating window has no decorator: $id")
          }
        }
        else if (info.isWindowed) {
          val entry = idToEntry[id]
          if (entry != null && entry.windowedDecorator == null) {
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

private fun createInitializingLabel(): JLabel {
  val label = JLabel("Initializing...", SwingConstants.CENTER)
  label.isOpaque = true
  val treeBg = UIUtil.getTreeBackground()
  label.background = ColorUtil.toAlpha(treeBg, 180)
  val treeFg = UIUtil.getTreeForeground()
  label.foreground = ColorUtil.toAlpha(treeFg, 180)
  return label
}

private fun isToHideOnDeactivation(info: WindowInfoImpl): Boolean {
  return if (info.isFloating || info.isWindowed) false else info.isAutoHide || info.isSliding
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