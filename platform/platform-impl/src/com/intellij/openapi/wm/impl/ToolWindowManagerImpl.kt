// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.BundleBase
import com.intellij.DynamicBundle
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.runActivity
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.UiActivity
import com.intellij.ide.UiActivityMonitor
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.actions.MaximizeActiveDialogAction
import com.intellij.ide.plugins.PluginManagerCore
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
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
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
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.ui.BalloonImpl
import com.intellij.ui.ComponentUtil
import com.intellij.ui.GuiUtils
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.BitUtil
import com.intellij.util.EventDispatcher
import com.intellij.util.SingleAlarm
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.*
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
import java.util.function.Supplier
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

private val LOG = logger<ToolWindowManagerImpl>()

@State(
  name = "ToolWindowManager",
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
  private var waiterForSecondPress: SingleAlarm? = null

  private val pendingSetLayoutTask = AtomicReference<Runnable?>()

  fun isToolWindowRegistered(id: String) = idToEntry.containsKey(id)

  init {
    if (project.isDefault) {
      waiterForSecondPress = null
    }
    else {
      runActivity("toolwindow factory class preloading") {
        processDescriptors { bean, pluginDescriptor  ->
          bean.getToolWindowFactory(pluginDescriptor)
        }
      }
    }
  }

  companion object {
    @JvmStatic
    @ApiStatus.Internal
    fun getRegisteredMutableInfoOrLogError(decorator: InternalDecorator): WindowInfoImpl {
      val toolWindow = decorator.toolWindow
      return toolWindow.toolWindowManager.getRegisteredMutableInfoOrLogError(toolWindow.id)
    }
  }

  internal fun getFrame() = frame

  @Service
  private class ToolWindowManagerAppLevelHelper {
    companion object {
      private fun handleFocusEvent(event: FocusEvent) {
        if (event.id == FocusEvent.FOCUS_LOST) {
          if (event.oppositeComponent == null || event.isTemporary) {
            return
          }

          val project = IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project ?: return
          if (project.isDisposed || project.isDefault) {
            return
          }

          val toolWindowManager = getInstance(project) as ToolWindowManagerImpl
          val toolWindowId = toolWindowManager.activeToolWindowId ?: return

          val activeEntry = toolWindowManager.idToEntry.get(toolWindowId) ?: return
          val windowInfo = activeEntry.readOnlyWindowInfo
          // just removed
          if (!windowInfo.isVisible) {
            return
          }

          if (!(windowInfo.isAutoHide || windowInfo.type == ToolWindowType.SLIDING)) {
            return
          }

          // let's check that it is a toolwindow who loses the focus
          if (isInActiveToolWindow(event.source, activeEntry.toolWindow) && !isInActiveToolWindow(event.oppositeComponent, activeEntry.toolWindow)) {
            // a toolwindow lost focus
            val focusGoesToPopup = JBPopupFactory.getInstance().getParentBalloonFor(event.oppositeComponent) != null
            if (!focusGoesToPopup) {
              val info = toolWindowManager.getRegisteredMutableInfoOrLogError(toolWindowId)
              toolWindowManager.doDeactivateToolWindow(info, activeEntry)
            }
          }
        }
        else if (event.id == FocusEvent.FOCUS_GAINED) {
          val component = event.component ?: return
          processOpenedProjects { project ->
            for (composite in FileEditorManagerEx.getInstanceEx(project).splitters.editorComposites) {
              if (composite.editors.any { SwingUtilities.isDescendingFrom(component, it.component) }) {
                (getInstance(project) as ToolWindowManagerImpl).activeStack.clear()
              }
            }
          }
        }
      }

      private inline fun process(processor: (manager: ToolWindowManagerImpl) -> Unit) {
        processOpenedProjects { project ->
          processor(getInstance(project) as ToolWindowManagerImpl)
        }
      }
    }

    init {
      val awtFocusListener = AWTEventListener { event ->
        handleFocusEvent(event as FocusEvent)
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
        override fun projectClosingBeforeSave(project: Project) {
          val manager = (project.serviceIfCreated<ToolWindowManager>() as ToolWindowManagerImpl?) ?: return
          for (entry in manager.idToEntry.values) {
            manager.saveFloatingOrWindowedState(entry, manager.layout.getInfo(entry.id) ?: continue)
          }
        }

        override fun projectClosed(project: Project) {
          (project.serviceIfCreated<ToolWindowManager>() as ToolWindowManagerImpl?)?.projectClosed()
        }
      })

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
            val frame = event.getSource() as? JFrame
            if (frame === manager.frame?.frame) {
              manager.resetHoldState()
            }
          }
        }

        false
      }, ApplicationManager.getApplication())
    }
  }

  private fun updateToolWindowHeaders() {
    focusManager.doWhenFocusSettlesDown(ExpirableRunnable.forProject(project) {
      for (entry in idToEntry.values) {
        if (entry.readOnlyWindowInfo.isVisible) {
          entry.toolWindow.decoratorComponent?.repaint()
        }
      }
    })
  }

  private fun dispatchKeyEvent(e: KeyEvent): Boolean {
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
        waiterForSecondPress?.cancelAndRequest()
      }
      else {
        resetHoldState()
      }
    }
  }

  private fun processHoldState() {
    toolWindowPane?.setStripesOverlayed(currentState == KeyState.HOLD)
  }

  @ApiStatus.Internal
  override fun init(frameHelper: ProjectFrameHelper): ToolWindowsPane {
    toolWindowPane?.let {
      return it
    }

    // manager is used in light tests (light project is never disposed), so, earlyDisposable must be used
    val disposable = (project as ProjectEx).earlyDisposable
    waiterForSecondPress = SingleAlarm(task = Runnable {
      if (currentState != KeyState.HOLD) {
        resetHoldState()
      }
    }, delay = SystemProperties.getIntProperty("actionSystem.keyGestureDblClickTime", 650), parentDisposable = disposable)

    val connection = project.messageBus.connect(disposable)
    connection.subscribe(ToolWindowManagerListener.TOPIC, dispatcher.multicaster)
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        focusManager.doWhenFocusSettlesDown(ExpirableRunnable.forProject(project) {
          if (!FileEditorManager.getInstance(project).hasOpenFiles()) {
            focusToolWindowByDefault()
          }
        })
      }
    })

    frame = frameHelper
    val rootPane = frameHelper.rootPane!!
    val toolWindowPane = rootPane.toolWindowPane
    toolWindowPane.initDocumentComponent(project)
    this.toolWindowPane = toolWindowPane
    return toolWindowPane
  }

  private fun beforeProjectOpened() {
    LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread)

    val list = mutableListOf<RegisterToolWindowTask>()
    runActivity("toolwindow init command creation") {
      processDescriptors { bean, pluginDescriptor  ->
        val condition = bean.getCondition(pluginDescriptor)
        // compute outside of EDT
        if (condition != null && !condition.value(project)) {
          return@processDescriptors
        }

        val factory = bean.getToolWindowFactory(pluginDescriptor)
        if (!factory.isApplicable(project)) {
          return@processDescriptors
        }

        @Suppress("DEPRECATION")
        val sideTool = bean.secondary || bean.side
        list.add(RegisterToolWindowTask(
          id = bean.id,
          icon = findIconFromBean(bean, factory),
          anchor = getToolWindowAnchor(factory, bean),
          sideTool = sideTool,
          canCloseContent = bean.canCloseContents,
          canWorkInDumbMode = DumbService.isDumbAware(factory),
          shouldBeAvailable = factory.shouldBeAvailable(project),
          contentFactory = factory,
          stripeTitle = getStripeTitleSupplier(bean.id, pluginDescriptor)
        ))
      }
    }

    // must be executed in EDT
    ApplicationManager.getApplication().invokeLater(Runnable {
      frame!!.rootPane!!.updateToolbar()

      pendingSetLayoutTask.getAndSet(null)?.run()

      if (toolWindowPane == null) {
        if (!ApplicationManager.getApplication().isUnitTestMode) {
          LOG.warn("ProjectFrameAllocator is not used - use PlatformProjectOpenProcessor.openExistingProject to open project in a correct way")
        }
        val toolWindowsPane = init((WindowManager.getInstance() as WindowManagerImpl).allocateFrame(project))

        // cannot be executed because added layered pane is not yet validated and size is not known
        ApplicationManager.getApplication().invokeLater(Runnable {
          pendingSetLayoutTask.getAndSet(null)?.run()
          initToolWindows(list, toolWindowsPane)
        }, project.disposed)
      }
      else {
        initToolWindows(list, toolWindowPane!!)
      }

      registerEPListeners()
    }, project.disposed)
  }

  // This method cannot be inlined because of magic Kotlin compilation bug: it 'captured' "list" local value and cause class-loader leak
  // See IDEA-CR-61904
  private fun registerEPListeners() {
    ToolWindowEP.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<ToolWindowEP> {
      override fun extensionAdded(extension: ToolWindowEP, pluginDescriptor: PluginDescriptor) {
        initToolWindow(extension, pluginDescriptor)
      }

      override fun extensionRemoved(extension: ToolWindowEP, pluginDescriptor: PluginDescriptor) {
        doUnregisterToolWindow(extension.id)
      }
    }, project)
  }

  private fun getToolWindowAnchor(factory: ToolWindowFactory?, bean: ToolWindowEP) =
    (factory as? ToolWindowFactoryEx)?.anchor ?: ToolWindowAnchor.fromText(bean.anchor ?: ToolWindowAnchor.LEFT.toString())

  private fun initToolWindows(list: List<RegisterToolWindowTask>, toolWindowsPane: ToolWindowsPane) {
    runActivity("toolwindow creating") {

      val entries = ArrayList<String>(list.size)
      for (task in list) {
        try {
          entries.add(doRegisterToolWindow(task, toolWindowsPane).id)
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (t: Throwable) {
          LOG.error("Cannot init toolwindow ${task.contentFactory}", t)
        }
      }

      project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowsRegistered(entries)
      toolWindowPane!!.revalidateNotEmptyStripes()
    }

    toolWindowsPane.putClientProperty(UIUtil.NOT_IN_HIERARCHY_COMPONENTS, Iterable {
      idToEntry.values.asSequence().mapNotNull {
        val component = it.toolWindow.decoratorComponent
        if (component != null && component.parent == null) component else null
      }.iterator()
    })

    service<ToolWindowManagerAppLevelHelper>()
  }

  override fun initToolWindow(bean: ToolWindowEP) {
    initToolWindow(bean, bean.pluginDescriptor)
  }

  private fun initToolWindow(bean: ToolWindowEP, pluginDescriptor: PluginDescriptor) {
    val condition = bean.getCondition(pluginDescriptor)
    if (condition != null && !condition.value(project)) {
      return
    }

    val factory = bean.getToolWindowFactory(bean.pluginDescriptor)
    if (!factory.isApplicable(project)) {
      return
    }

    val toolWindowPane = toolWindowPane ?: init((WindowManager.getInstance() as WindowManagerImpl).allocateFrame(project))
    val anchor = getToolWindowAnchor(factory, bean)
    @Suppress("DEPRECATION")
    val sideTool = bean.secondary || bean.side
    val entry = doRegisterToolWindow(RegisterToolWindowTask(
      id = bean.id,
      icon = findIconFromBean(bean, factory),
      anchor = anchor,
      sideTool = sideTool,
      canCloseContent = bean.canCloseContents,
      canWorkInDumbMode = DumbService.isDumbAware(factory),
      shouldBeAvailable = factory.shouldBeAvailable(project),
      contentFactory = factory,
      stripeTitle = getStripeTitleSupplier(bean.id, pluginDescriptor)
    ), toolWindowPane)
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowsRegistered(listOf(entry.id))

    toolWindowPane.getStripeFor(anchor).revalidate()
    toolWindowPane.validate()
    toolWindowPane.repaint()
  }

  fun projectClosed() {
    if (frame == null) {
      return
    }

    frame!!.releaseFrame()

    toolWindowPane!!.putClientProperty(UIUtil.NOT_IN_HIERARCHY_COMPONENTS, null)

    // hide all tool windows - frame maybe reused for another project
    for (entry in idToEntry.values) {
      try {
        removeDecoratorWithoutUpdatingState(entry, layout.getInfo(entry.id) ?: continue, dirtyMode = true)
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
      finally {
        Disposer.dispose(entry.disposable)
      }
    }

    toolWindowPane!!.reset()

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

  override fun activateEditorComponent() {
    if (!EditorsSplitters.focusDefaultComponentInSplittersIfPresent(project)) {
      // see note about requestFocus in focusDefaultComponentInSplittersIfPresent
      frame?.rootPane?.requestFocus()
    }
  }

  internal fun activateToolWindow(id: String, runnable: Runnable?, autoFocusContents: Boolean) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    val activity = UiActivity.Focus("toolWindow:$id")
    UiActivityMonitor.getInstance().addActivity(project, activity, ModalityState.NON_MODAL)

    activateToolWindow(idToEntry.get(id)!!, getRegisteredMutableInfoOrLogError(id), autoFocusContents)

    ApplicationManager.getApplication().invokeLater(Runnable {
      runnable?.run()
      UiActivityMonitor.getInstance().removeActivity(project, activity)
    }, project.disposed)
  }

  private fun activateToolWindow(entry: ToolWindowEntry, info: WindowInfoImpl, autoFocusContents: Boolean = true) {
    LOG.debug { "activateToolWindow($entry)" }

    ToolWindowCollector.getInstance().recordActivation(entry.id, info)

    if (!entry.toolWindow.isAvailable) {
      // Tool window can be "logically" active but not focused. For example,
      // when the user switched to another application. So we just need to bring
      // tool window's window to front.
      if (autoFocusContents && !entry.toolWindow.hasFocus) {
        entry.toolWindow.requestFocusInToolWindow()
      }

      return
    }

    if (!entry.readOnlyWindowInfo.isVisible) {
      showToolWindowImpl(entry, info, dirtyMode = false)
    }

    if (autoFocusContents && ApplicationManager.getApplication().isActive) {
      entry.toolWindow.requestFocusInToolWindow()
    }
    else {
      activeStack.push(entry)
    }

    fireStateChanged()
  }

  internal fun updateToolWindow(toolWindow: ToolWindowImpl, component: Component) {
    toolWindow.setFocusedComponent(component)
    if (toolWindow.isAvailable && !toolWindow.isActive) {
      activateToolWindow(toolWindow.id, null, autoFocusContents = true)
    }
    activeStack.push(idToEntry.get(toolWindow.id) ?: return)
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

  private fun doDeactivateToolWindow(info: WindowInfoImpl, entry: ToolWindowEntry, dirtyMode: Boolean = false) {
    LOG.debug { "enter: deactivateToolWindowImpl(${info.id})" }

    setHiddenState(info, entry)
    updateStateAndRemoveDecorator(info, entry, dirtyMode = dirtyMode)

    entry.applyWindowInfo(info.copy())
  }

  private fun setHiddenState(info: WindowInfoImpl, entry: ToolWindowEntry) {
    ToolWindowCollector.getInstance().recordHidden(info)

    info.isActiveOnStart = false
    info.isVisible = false
    activeStack.remove(entry, false)
  }

  override val toolWindowIds: Array<String>
    get() = idToEntry.keys.toTypedArray()

  override val toolWindowIdSet: Set<String>
    get() = HashSet(idToEntry.keys)

  override val activeToolWindowId: String?
    get() {
      EDT.assertIsEdt()
      val frame = frame?.frame ?: return null
      if (frame.isActive) {
        val focusOwner = focusManager.getLastFocusedFor(frame) ?: return null
        var parent: Component? = focusOwner
        while (parent != null) {
          if (parent is InternalDecorator) {
            return parent.toolWindow.id
          }

          parent = parent.parent
        }
      }
      else {
        // let's check floating and windowed
        for (entry in idToEntry.values) {
          if (entry.floatingDecorator?.isActive == true || entry.windowedDecorator?.isActive == true) {
            return entry.id
          }
        }
      }
      return null
    }

  override val lastActiveToolWindowId: String?
    get() = getLastActiveToolWindow(condition = null)?.id

  override fun getLastActiveToolWindow(condition: Predicate<JComponent>?): ToolWindow? {
    EDT.assertIsEdt()
    for (i in 0 until activeStack.persistentSize) {
      val toolWindow = activeStack.peekPersistent(i).toolWindow
      if (toolWindow.isAvailable && (condition == null || condition.test(toolWindow.component))) {
        return toolWindow
      }
    }
    return null
  }

  /**
   * @return floating decorator for the tool window with specified `ID`.
   */
  private fun getFloatingDecorator(id: String) = idToEntry.get(id)?.floatingDecorator

  /**
   * @return windowed decorator for the tool window with specified `ID`.
   */
  private fun getWindowedDecorator(id: String) = idToEntry.get(id)?.windowedDecorator

  /**
   * @return tool button for the window with specified `ID`.
   */
  @TestOnly
  fun getStripeButton(id: String) = idToEntry.get(id)!!.stripeButton

  override fun getIdsOn(anchor: ToolWindowAnchor) = getVisibleToolWindowsOn(anchor).map { it.id }.toList()

  @ApiStatus.Internal
  fun getToolWindowsOn(anchor: ToolWindowAnchor, excludedId: String): List<ToolWindowEx> {
    return getVisibleToolWindowsOn(anchor)
      .filter { it.id != excludedId  }
      .map { it.toolWindow }
      .toList()
  }

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
    val info = layout.getInfo(id) ?: return fallbackIcon
    val type = info.type
    if (type == ToolWindowType.FLOATING || type == ToolWindowType.WINDOWED) {
      return AllIcons.Actions.MoveToWindow
    }

    val anchor = info.anchor
    val splitMode = info.isSplit
    return when (anchor) {
      ToolWindowAnchor.BOTTOM -> {
        if (splitMode) AllIcons.Actions.MoveToBottomRight else AllIcons.Actions.MoveToBottomLeft
      }
      ToolWindowAnchor.LEFT -> {
        if (splitMode) AllIcons.Actions.MoveToLeftBottom else AllIcons.Actions.MoveToLeftTop
      }
      ToolWindowAnchor.RIGHT -> {
        if (splitMode) AllIcons.Actions.MoveToRightBottom else AllIcons.Actions.MoveToRightTop
      }
      ToolWindowAnchor.TOP -> {
        if (splitMode) AllIcons.Actions.MoveToTopRight else AllIcons.Actions.MoveToTopLeft
      }
      else -> fallbackIcon
    }
  }

  private fun getVisibleToolWindowsOn(anchor: ToolWindowAnchor): Sequence<ToolWindowEntry> {
    return idToEntry.values
      .asSequence()
      .filter { it.readOnlyWindowInfo.anchor == anchor && it.toolWindow.isAvailable }
  }

  // cannot be ToolWindowEx because of backward compatibility
  override fun getToolWindow(id: String?): ToolWindow? {
    return idToEntry.get(id ?: return null)?.toolWindow
  }

  fun showToolWindow(id: String) {
    LOG.debug { "enter: showToolWindow($id)" }
    EDT.assertIsEdt()
    val info = layout.getInfo(id) ?: throw IllegalThreadStateException("window with id=\"$id\" is unknown")
    val entry = idToEntry.get(id)!!
    if (entry.readOnlyWindowInfo.isVisible) {
      LOG.assertTrue(entry.toolWindow.getComponentIfInitialized() != null)
      return
    }

    if (showToolWindowImpl(entry, info, dirtyMode = false)) {
      checkInvariants("id: $id")
      fireStateChanged()
    }
  }

  internal fun removeFromSideBar(id: String) {
    val info = getRegisteredMutableInfoOrLogError(id)
    if (!info.isShowStripeButton) {
      return
    }

    val entry = idToEntry.get(info.id!!)!!

    info.isShowStripeButton = false
    setHiddenState(info, entry)
    updateStateAndRemoveDecorator(info, entry, dirtyMode = false)
    entry.applyWindowInfo(info.copy())

    fireStateChanged()
  }

  override fun hideToolWindow(id: String, hideSide: Boolean) {
    hideToolWindow(id, hideSide, moveFocus = true)
  }

  fun hideToolWindow(id: String, hideSide: Boolean, moveFocus: Boolean) {
    EDT.assertIsEdt()

    val entry = idToEntry.get(id)!!
    if (!entry.readOnlyWindowInfo.isVisible) {
      return
    }

    val info = getRegisteredMutableInfoOrLogError(id)
    val moveFocusAfter = moveFocus && entry.toolWindow.isActive
    doHide(entry, info, dirtyMode = false, hideSide = hideSide)
    fireStateChanged()
    if (moveFocusAfter) {
      activateEditorComponent()
    }
  }

  private fun doHide(entry: ToolWindowEntry, info: WindowInfoImpl, dirtyMode: Boolean, hideSide: Boolean = false) {
    // hide and deactivate
    doDeactivateToolWindow(info, entry, dirtyMode = dirtyMode)

    if (hideSide && info.type != ToolWindowType.FLOATING && info.type != ToolWindowType.WINDOWED) {
      for (each in getVisibleToolWindowsOn(info.anchor)) {
        activeStack.remove(each, false)
      }
      if (isStackEnabled) {
        while (!sideStack.isEmpty(info.anchor)) {
          sideStack.pop(info.anchor)
        }
      }
      for (otherEntry in idToEntry.values) {
        val otherInfo = layout.getInfo(otherEntry.id) ?: continue
        if (otherInfo.isVisible && otherInfo.anchor == info.anchor) {
          doDeactivateToolWindow(otherInfo, otherEntry, dirtyMode = dirtyMode)
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
          val entry2 = idToEntry.get(info2.id!!)!!
          if (!entry2.readOnlyWindowInfo.isVisible) {
            showToolWindowImpl(entry2, info2, dirtyMode = dirtyMode)
          }
        }
      }
      activeStack.remove(entry, false)
    }
  }

  /**
   * @param dirtyMode if `true` then all UI operations are performed in dirty mode.
   */
  private fun showToolWindowImpl(entry: ToolWindowEntry, toBeShownInfo: WindowInfoImpl, dirtyMode: Boolean): Boolean {
    if (!entry.toolWindow.isAvailable) {
      return false
    }

    ToolWindowCollector.getInstance().recordShown(toBeShownInfo)
    toBeShownInfo.isVisible = true
    toBeShownInfo.isShowStripeButton = true

    val snapshotInfo = toBeShownInfo.copy()
    entry.applyWindowInfo(snapshotInfo)
    doShowWindow(entry, snapshotInfo, dirtyMode)

    if (entry.readOnlyWindowInfo.type == ToolWindowType.WINDOWED && entry.toolWindow.getComponentIfInitialized() != null) {
      UIUtil.toFront(ComponentUtil.getWindow(entry.toolWindow.component))
    }
    return true
  }

  private fun doShowWindow(entry: ToolWindowEntry, info: WindowInfo, dirtyMode: Boolean) {
    if (entry.readOnlyWindowInfo.type == ToolWindowType.FLOATING) {
      addFloatingDecorator(entry, info)
    }
    else if (entry.readOnlyWindowInfo.type == ToolWindowType.WINDOWED) {
      addWindowedDecorator(entry, info)
    }
    else {
      // docked and sliding windows
      // If there is tool window on the same side then we have to hide it, i.e.
      // clear place for tool window to be shown.
      //
      // We store WindowInfo of hidden tool window in the SideStack (if the tool window
      // is docked and not auto-hide one). Therefore it's possible to restore the
      // hidden tool window when showing tool window will be closed.
      for (otherEntry in idToEntry.values) {
        if (entry.id == otherEntry.id) {
          continue
        }

        val otherInfo = otherEntry.readOnlyWindowInfo
        if (otherInfo.isVisible && otherInfo.type == info.type && otherInfo.anchor == info.anchor && otherInfo.isSplit == info.isSplit) {
          val otherLayoutInto = layout.getInfo(otherEntry.id)!!
          // hide and deactivate tool window
          setHiddenState(otherLayoutInto, otherEntry)

          val otherInfoCopy = otherLayoutInto.copy()
          otherEntry.applyWindowInfo(otherInfoCopy)
          otherEntry.toolWindow.decoratorComponent?.let { decorator ->
            toolWindowPane!!.removeDecorator(otherInfoCopy, decorator, false, this)
          }

          // store WindowInfo into the SideStack
          if (isStackEnabled && otherInfo.isDocked && !otherInfo.isAutoHide) {
            sideStack.push(otherInfoCopy)
          }
        }
      }

      toolWindowPane!!.addDecorator(entry.toolWindow.getOrCreateDecoratorComponent(), info, dirtyMode, this)
      // remove tool window from the SideStack
      if (isStackEnabled) {
        sideStack.remove(entry.id)
      }
    }

    entry.toolWindow.scheduleContentInitializationIfNeeded()
    fireToolWindowShown(entry.id, entry.toolWindow)
  }

  override fun registerToolWindow(task: RegisterToolWindowTask): ToolWindow {
    val entry = doRegisterToolWindow(task,
      toolWindowPane = toolWindowPane ?: init((WindowManager.getInstance() as WindowManagerImpl).allocateFrame(project)))
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowsRegistered(listOf(entry.id))
    val toolWindowPane = toolWindowPane!!
    toolWindowPane.getStripeFor(entry.toolWindow.anchor).revalidate()
    toolWindowPane.validate()
    toolWindowPane.repaint()

    fireStateChanged()
    return entry.toolWindow
  }

  private fun doRegisterToolWindow(task: RegisterToolWindowTask, toolWindowPane: ToolWindowsPane): ToolWindowEntry {
    LOG.debug { "enter: installToolWindow($task)" }

    ApplicationManager.getApplication().assertIsDispatchThread()
    if (idToEntry.containsKey(task.id)) {
      throw IllegalArgumentException("window with id=\"${task.id}\" is already registered")
    }

    val info = layout.getOrCreate(task)
    val disposable = Disposer.newDisposable(task.id)
    Disposer.register(project, disposable)

    val contentFactory = task.contentFactory

    val windowInfoSnapshot = info.copy()
    if (windowInfoSnapshot.isVisible && (contentFactory == null || !task.shouldBeAvailable)) {
      // isVisible cannot be true if contentFactory is null, because we cannot show toolwindow without content
      windowInfoSnapshot.isVisible = false
    }

    val toolWindow = ToolWindowImpl(this, task.id, task.canCloseContent, task.canWorkInDumbMode, task.component, disposable,
      windowInfoSnapshot, contentFactory, isAvailable = task.shouldBeAvailable, stripeTitle = task.stripeTitle?.get() ?: task.id)

    toolWindow.windowInfoDuringInit = windowInfoSnapshot
    try {
      contentFactory?.init(toolWindow)
    }
    finally {
      toolWindow.windowInfoDuringInit = null
    }

    // contentFactory.init can set icon
    if (toolWindow.icon == null) {
      task.icon?.let {
        toolWindow.doSetIcon(it)
      }
    }

    val button = StripeButton(toolWindowPane, toolWindow)
    val entry = ToolWindowEntry(button, toolWindow, disposable)
    idToEntry.put(task.id, entry)

    // only after added to idToEntry map
    button.isSelected = windowInfoSnapshot.isVisible
    button.updatePresentation()
    addStripeButton(button, toolWindowPane.getStripeFor((contentFactory as? ToolWindowFactoryEx)?.anchor ?: info.anchor))

    // If preloaded info is visible or active then we have to show/activate the installed
    // tool window. This step has sense only for windows which are not in the auto hide
    // mode. But if tool window was active but its mode doesn't allow to activate it again
    // (for example, tool window is in auto hide mode) then we just activate editor component.
    if (contentFactory != null /* not null on init tool window from EP */) {
      if (windowInfoSnapshot.isVisible) {
        showToolWindowImpl(entry, info, dirtyMode = false)

        // do not activate tool window that is the part of project frame - default component should be focused
        if (windowInfoSnapshot.isActiveOnStart && (windowInfoSnapshot.type == ToolWindowType.WINDOWED || windowInfoSnapshot.type == ToolWindowType.FLOATING) && ApplicationManager.getApplication().isActive) {
          entry.toolWindow.requestFocusInToolWindow()
        }
      }
    }

    ActivateToolWindowAction.ensureToolWindowActionRegistered(toolWindow)
    return entry
  }

  @Suppress("OverridingDeprecatedMember")
  override fun unregisterToolWindow(id: String) {
    doUnregisterToolWindow(id)
    fireStateChanged()
  }

  internal fun doUnregisterToolWindow(id: String) {
    LOG.debug { "enter: unregisterToolWindow($id)" }

    ApplicationManager.getApplication().assertIsDispatchThread()

    val entry = idToEntry.remove(id) ?: return
    val toolWindow = entry.toolWindow

    val info = layout.getInfo(id)
    if (info != null) {
      // remove decorator and tool button from the screen - removing will also save current bounds
      updateStateAndRemoveDecorator(info, entry, false)
      // save recent appearance of tool window
      activeStack.remove(entry, true)
      if (isStackEnabled) {
        sideStack.remove(id)
      }
      removeStripeButton(entry.stripeButton)
      toolWindowPane!!.validate()
      toolWindowPane!!.repaint()
    }

    if (!project.isDisposed) {
      project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowUnregistered(id, (toolWindow))
    }

    Disposer.dispose(entry.disposable)
  }

  private fun updateStateAndRemoveDecorator(info: WindowInfoImpl, entry: ToolWindowEntry, dirtyMode: Boolean) {
    saveFloatingOrWindowedState(entry, info)

    removeDecoratorWithoutUpdatingState(entry, info, dirtyMode)
  }

  private fun removeDecoratorWithoutUpdatingState(entry: ToolWindowEntry, info: WindowInfoImpl, dirtyMode: Boolean) {
    entry.windowedDecorator?.let {
      entry.windowedDecorator = null
      Disposer.dispose(it)
      return
    }

    entry.floatingDecorator?.let {
      entry.floatingDecorator = null
      it.dispose()
      return
    }

    entry.toolWindow.decoratorComponent?.let {
      toolWindowPane!!.removeDecorator(info, it, dirtyMode, this)
      return
    }
  }

  private fun saveFloatingOrWindowedState(entry: ToolWindowEntry, info: WindowInfoImpl) {
    entry.floatingDecorator?.let {
      info.floatingBounds = it.bounds
      info.isActiveOnStart = it.isActive
      return
    }

    entry.windowedDecorator?.let { windowedDecorator ->
      info.isActiveOnStart = windowedDecorator.isActive
      val frame = windowedDecorator.getFrame()
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
      return
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

  override fun setLayout(newLayout: DesktopLayout) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    if (idToEntry.isEmpty()) {
      layout = newLayout
      return
    }

    data class LayoutData(val old: WindowInfoImpl, val new: WindowInfoImpl, val entry: ToolWindowEntry)

    val list = mutableListOf<LayoutData>()

    for (entry in idToEntry.values) {
      val old = layout.getInfo(entry.id) ?: entry.readOnlyWindowInfo as WindowInfoImpl
      val new = newLayout.getInfo(entry.id)
      // just copy if defined in the old layout but not in the new
      if (new == null) {
        newLayout.addInfo(entry.id, old.copy())
      }
      else if (old != new) {
        list.add(LayoutData(old = old, new = new, entry = entry))
      }
    }

    this.layout = newLayout

    if (list.isEmpty()) {
      return
    }

    for (item in list) {
      item.entry.applyWindowInfo(item.new)

      if (item.old.isVisible && !item.new.isVisible) {
        updateStateAndRemoveDecorator(item.new, item.entry, dirtyMode = true)
      }

      if (item.old.anchor != item.new.anchor || item.old.order != item.new.order) {
        setToolWindowAnchorImpl(item.entry, item.old, item.new, item.new.anchor, item.new.order)
      }

      var toShowWindow = false

      if (item.old.isSplit != item.new.isSplit) {
        val wasVisible = item.old.isVisible
        // we should hide the window and show it in a 'new place' to automatically hide possible window that is already located in a 'new place'
        if (wasVisible) {
          hideToolWindow(item.entry.id, hideSide = false, moveFocus = true)
        }

        if (wasVisible) {
          toShowWindow = true
        }
      }

      if (item.old.type != item.new.type) {
        val dirtyMode = item.old.type == ToolWindowType.DOCKED || item.old.type == ToolWindowType.SLIDING
        updateStateAndRemoveDecorator(item.old, item.entry, dirtyMode)
        if (item.new.isVisible) {
          toShowWindow = true
        }
      }
      else if (!item.old.isVisible && item.new.isVisible) {
        toShowWindow = true
      }

      if (toShowWindow) {
        doShowWindow(item.entry, item.new, dirtyMode = true)
      }
    }

    val toolWindowPane = toolWindowPane!!
    toolWindowPane.revalidateNotEmptyStripes()
    toolWindowPane.validate()
    toolWindowPane.repaint()

    activateEditorComponent()

    val frame = frame!!
    val rootPane = frame.rootPane ?: return
    rootPane.revalidate()
    rootPane.repaint()

    fireStateChanged()

    checkInvariants("")
  }

  override fun invokeLater(runnable: Runnable) {
    ApplicationManager.getApplication().invokeLater(runnable, project.disposed)
  }

  override val focusManager: IdeFocusManager
    get() = IdeFocusManager.getInstance(project)!!

  override fun canShowNotification(toolWindowId: String): Boolean {
    return toolWindowPane?.getStripeFor(idToEntry.get(toolWindowId)?.readOnlyWindowInfo?.anchor ?: return false)?.getButtonFor(toolWindowId) != null
  }

  override fun notifyByBalloon(toolWindowId: String, type: MessageType, htmlBody: String) {
    notifyByBalloon(toolWindowId, type, htmlBody, null, null)
  }

  override fun notifyByBalloon(options: ToolWindowBalloonShowOptions) {
    val entry = idToEntry.get(options.toolWindowId)!!
    val existing = entry.balloon
    if (existing != null) {
      Disposer.dispose(existing)
    }

    val stripe = toolWindowPane!!.getStripeFor(entry.readOnlyWindowInfo.anchor)
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

    val listenerWrapper = BalloonHyperlinkListener(options.listener)
    val balloonBuilder = JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(options.htmlBody.replace("\n", "<br>"), options.icon, options.type.titleForeground, options.type.popupBackground, listenerWrapper)
      .setBorderColor(options.type.borderColor)
      .setHideOnClickOutside(false)
      .setHideOnFrameResize(false)

    options.balloonCustomizer?.accept(balloonBuilder)

    val balloon = balloonBuilder.createBalloon() as BalloonImpl
    NotificationsManagerImpl.frameActivateBalloonListener(balloon, Runnable {
      AppExecutorUtil.getAppScheduledExecutorService().schedule({ balloon.setHideOnClickOutside(true) }, 100, TimeUnit.MILLISECONDS)
    })

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

    val button = stripe.getButtonFor(options.toolWindowId)
    LOG.assertTrue(button != null, ("Button was not found, popup won't be shown. $options"))
    if (button == null) {
      return
    }

    val show = Runnable {
      val tracker: PositionTracker<Balloon>
      if (button.isShowing) {
        tracker = object : PositionTracker<Balloon>(button) {
          override fun recalculateLocation(`object`: Balloon): RelativePoint? {
            val otherEntry = idToEntry.get(options.toolWindowId) ?: return null
            val stripeButton = otherEntry.stripeButton
            if (otherEntry.readOnlyWindowInfo.anchor != anchor) {
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

  override fun getToolWindowBalloon(id: String) = idToEntry.get(id)?.balloon

  override val isEditorComponentActive: Boolean
    get() {
      ApplicationManager.getApplication().assertIsDispatchThread()
      return ComponentUtil.getParentOfType(EditorsSplitters::class.java, focusManager.focusOwner) != null
    }

  fun setToolWindowAnchor(id: String, anchor: ToolWindowAnchor) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    setToolWindowAnchor(id, anchor, -1)
  }

  // used by Rider
  @Suppress("MemberVisibilityCanBePrivate")
  fun setToolWindowAnchor(id: String, anchor: ToolWindowAnchor, order: Int) {
    val entry = idToEntry.get(id)!!

    val info = entry.readOnlyWindowInfo
    if (anchor == info.anchor && (order == info.order || order == -1)) {
      return
    }

    ApplicationManager.getApplication().assertIsDispatchThread()
    setToolWindowAnchorImpl(entry, info, getRegisteredMutableInfoOrLogError(id), anchor, order)
    toolWindowPane!!.validateAndRepaint()
    fireStateChanged()
  }

  private fun setToolWindowAnchorImpl(entry: ToolWindowEntry,
                                      currentInfo: WindowInfo,
                                      layoutInfo: WindowInfoImpl,
                                      anchor: ToolWindowAnchor,
                                      order: Int) {
    // if tool window isn't visible or only order number is changed then just remove/add stripe button
    val toolWindowPane = toolWindowPane!!
    if (!currentInfo.isVisible || anchor == currentInfo.anchor || currentInfo.type == ToolWindowType.FLOATING || currentInfo.type == ToolWindowType.WINDOWED) {
      doSetAnchor(entry, layoutInfo, anchor, order)
    }
    else {
      val wasFocused = entry.toolWindow.isActive
      // for docked and sliding windows we have to move buttons and window's decorators
      layoutInfo.isVisible = false
      toolWindowPane.removeDecorator(currentInfo, entry.toolWindow.decoratorComponent, /* dirtyMode = */ true, this)

      doSetAnchor(entry, layoutInfo, anchor, order)

      showToolWindowImpl(entry, layoutInfo, false)
      if (wasFocused) {
        entry.toolWindow.requestFocusInToolWindow()
      }
    }
  }

  private fun doSetAnchor(entry: ToolWindowEntry, layoutInfo: WindowInfoImpl, anchor: ToolWindowAnchor, order: Int) {
    val toolWindowPane = toolWindowPane!!
    removeStripeButton(entry.stripeButton)

    layout.setAnchor(layoutInfo, anchor, order)
    // update infos for all window. Actually we have to update only infos affected by setAnchor method
    for (otherEntry in idToEntry.values) {
      val otherInfo = layout.getInfo(otherEntry.id)?.copy() ?: continue
      otherEntry.applyWindowInfo(otherInfo)
    }

    val stripe = toolWindowPane.getStripeFor(anchor)
    addStripeButton(entry.stripeButton, stripe)
    stripe.revalidate()
  }

  internal fun setSideTool(id: String, isSplit: Boolean) {
    val entry = idToEntry.get(id)
    if (entry == null) {
      LOG.error("Cannot set side tool: toolwindow $id is not registered")
      return
    }

    if (entry.readOnlyWindowInfo.isSplit != isSplit) {
      setSideTool(entry, getRegisteredMutableInfoOrLogError(id), isSplit)
      fireStateChanged()
    }
  }

  private fun setSideTool(entry: ToolWindowEntry, info: WindowInfoImpl, isSplit: Boolean) {
    if (isSplit == info.isSplit) {
      return
    }

    // we should hide the window and show it in a 'new place' to automatically hide possible window that is already located in a 'new place'
    hideIfNeededAndShowAfterTask(entry, info) {
      info.isSplit = isSplit
      for (otherEntry in idToEntry.values) {
        otherEntry.applyWindowInfo((layout.getInfo(otherEntry.id) ?: continue).copy())
      }
    }
    toolWindowPane!!.getStripeFor(entry.readOnlyWindowInfo.anchor).revalidate()
  }

  fun setContentUiType(id: String, type: ToolWindowContentUiType) {
    val info = getRegisteredMutableInfoOrLogError(id)
    info.contentUiType = type
    idToEntry.get(info.id!!)!!.applyWindowInfo(info.copy())
    fireStateChanged()
  }

  fun setSideToolAndAnchor(id: String, anchor: ToolWindowAnchor, order: Int, isSplit: Boolean) {
    val entry = idToEntry.get(id)!!
    val info = getRegisteredMutableInfoOrLogError(id)

    if (anchor == entry.readOnlyWindowInfo.anchor && order == entry.readOnlyWindowInfo.order && entry.readOnlyWindowInfo.isSplit == isSplit) {
      return
    }

    hideIfNeededAndShowAfterTask(entry, info) {
      info.isSplit = isSplit
      doSetAnchor(entry, info, anchor, order)
    }
    fireStateChanged()
  }

  private fun hideIfNeededAndShowAfterTask(entry: ToolWindowEntry, info: WindowInfoImpl, task: () -> Unit) {
    val wasVisible = entry.readOnlyWindowInfo.isVisible
    val wasFocused = entry.toolWindow.isActive
    if (wasVisible) {
      doHide(entry, info, dirtyMode = true)
    }

    task()

    if (wasVisible) {
      ToolWindowCollector.getInstance().recordShown(info)
      info.isVisible = true
      val infoSnapshot = info.copy()
      entry.applyWindowInfo(infoSnapshot)
      doShowWindow(entry, infoSnapshot, dirtyMode = true)
      if (wasFocused) {
        getShowingComponentToRequestFocus(entry.toolWindow)?.requestFocusInWindow()
      }
    }

    toolWindowPane!!.validateAndRepaint()
  }

  protected open fun fireStateChanged() {
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).stateChanged(this)
  }

  private fun fireToolWindowShown(id: String, toolWindow: ToolWindow) {
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowShown(id, toolWindow)
  }

  internal fun setToolWindowAutoHide(id: String, autoHide: Boolean) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    val info = getRegisteredMutableInfoOrLogError(id)
    if (info.isAutoHide == autoHide) {
      return
    }

    info.isAutoHide = autoHide
    val entry = idToEntry.get(id) ?: return

    val newInfo = info.copy()
    entry.applyWindowInfo(newInfo)

    fireStateChanged()
  }

  fun setToolWindowType(id: String, type: ToolWindowType) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    val entry = idToEntry.get(id)!!
    if (entry.readOnlyWindowInfo.type == type) {
      return
    }

    setToolWindowTypeImpl(entry, getRegisteredMutableInfoOrLogError(entry.id), type)
    fireStateChanged()
  }

  private fun setToolWindowTypeImpl(entry: ToolWindowEntry, info: WindowInfoImpl, type: ToolWindowType) {
    if (!entry.readOnlyWindowInfo.isVisible) {
      info.type = type
      entry.applyWindowInfo(info.copy())
      return
    }

    val dirtyMode = entry.readOnlyWindowInfo.type == ToolWindowType.DOCKED || entry.readOnlyWindowInfo.type == ToolWindowType.SLIDING
    updateStateAndRemoveDecorator(info, entry, dirtyMode)
    info.type = type

    val newInfo = info.copy()
    entry.applyWindowInfo(newInfo)
    doShowWindow(entry, newInfo, dirtyMode)

    if (ApplicationManager.getApplication().isActive) {
      entry.toolWindow.requestFocusInToolWindow()
    }

    val frame = frame!!
    val rootPane = frame.rootPane ?: return
    rootPane.revalidate()
    rootPane.repaint()
  }

  override fun clearSideStack() {
    if (isStackEnabled) {
      sideStack.clear()
    }
  }

  override fun getState(): Element? {
    // do nothing if the project was not opened
    if (frame == null) {
      return null
    }

    val element = Element("state")
    if (isEditorComponentActive) {
      element.addContent(Element(EDITOR_ELEMENT).setAttribute(ACTIVE_ATTR_VALUE, "true"))
    }

    // save layout of tool windows
    layout.writeExternal(DesktopLayout.TAG)?.let {
      element.addContent(it)
    }

    layoutToRestoreLater?.writeExternal(LAYOUT_TO_RESTORE)?.let {
      element.addContent(it)
    }
    return element
  }

  override fun noStateLoaded() {
    scheduleSetLayout(WindowManagerEx.getInstanceEx().layout.copy())
  }

  override fun loadState(state: Element) {
    for (element in state.children) {
      if (DesktopLayout.TAG == element.name) {
        val layout = DesktopLayout()
        layout.readExternal(element)
        scheduleSetLayout(layout)
      }
      else if (LAYOUT_TO_RESTORE == element.name) {
        layoutToRestoreLater = DesktopLayout()
        layoutToRestoreLater!!.readExternal(element)
      }
    }
  }

  private fun scheduleSetLayout(newLayout: DesktopLayout) {
    val app = ApplicationManager.getApplication()
    val task = Runnable {
      setLayout(newLayout)
    }

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

  internal fun setDefaultState(toolWindow: ToolWindowImpl, anchor: ToolWindowAnchor?, type: ToolWindowType?, floatingBounds: Rectangle?) {
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

  internal fun setDefaultContentUiType(toolWindow: ToolWindowImpl, type: ToolWindowContentUiType) {
    val info = getRegisteredMutableInfoOrLogError(toolWindow.id)
    if (info.isFromPersistentSettings) {
      return
    }
    toolWindow.setContentUiType(type, null)
  }

  internal fun stretchWidth(toolWindow: ToolWindowImpl, value: Int) {
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
      val frame = if (decorator != null && decorator.getFrame() is Frame) decorator.getFrame() as Frame else return
      val state = frame.state
      if (state == Frame.NORMAL) {
        frame.state = Frame.MAXIMIZED_BOTH
      }
      else if (state == Frame.MAXIMIZED_BOTH) {
        frame.state = Frame.NORMAL
      }
      return
    }
    toolWindowPane!!.setMaximized(window, maximized)
  }

  internal fun stretchHeight(toolWindow: ToolWindowImpl?, value: Int) {
    toolWindowPane!!.stretchHeight((toolWindow)!!, value)
  }

  private class BalloonHyperlinkListener constructor(private val listener: HyperlinkListener?) : HyperlinkListener {
    var balloon: Balloon? = null

    override fun hyperlinkUpdate(e: HyperlinkEvent) {
      val balloon = balloon
      if (balloon != null && e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
        balloon.hide()
      }
      listener?.hyperlinkUpdate(e)
    }
  }

  private fun addFloatingDecorator(entry: ToolWindowEntry, info: WindowInfo) {
    val frame = frame!!.frame
    val floatingDecorator = FloatingDecorator(frame, entry.toolWindow.getOrCreateDecoratorComponent() as InternalDecorator)
    floatingDecorator.apply(info)

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

    @Suppress("DEPRECATION")
    floatingDecorator.show()
  }

  private fun addWindowedDecorator(entry: ToolWindowEntry, info: WindowInfo) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }

    val id = entry.id
    val decorator = entry.toolWindow.getOrCreateDecoratorComponent()
    val windowedDecorator = FrameWrapper(project, title = "$id - ${project.name}", component = decorator)
    val window = windowedDecorator.getFrame()

    MnemonicHelper.init((window as RootPaneContainer).contentPane)

    val shouldBeMaximized = info.isMaximized
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
    Disposer.register(windowedDecorator, Disposable {
      if (idToEntry.get(id)?.windowedDecorator != null) {
        hideToolWindow(id, false)
      }
    })
    windowedDecorator.show(false)

    val rootPane = (window as RootPaneContainer).rootPane
    val rootPaneBounds = rootPane.bounds
    val point = rootPane.locationOnScreen
    val windowBounds = window.bounds
    window.setLocation(2 * windowBounds.x - point.x, 2 * windowBounds.y - point.y)
    window.setSize(2 * windowBounds.width - rootPaneBounds.width, 2 * windowBounds.height - rootPaneBounds.height)
    if (shouldBeMaximized && window is Frame) {
      window.extendedState = Frame.MAXIMIZED_BOTH
    }
    window.toFront()
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

    override fun isFocusedComponentChangeValid(component: Component?, cause: AWTEvent?) = component != null

    override fun focusedComponentChanged(component: Component?, cause: AWTEvent?) {
      if (component == null || !toolWindow.isActive) {
        return
      }

      val toolWindowManager = toolWindow.toolWindowManager
      toolWindowManager.focusManager
        .doWhenFocusSettlesDown(ExpirableRunnable.forProject(toolWindowManager.project) {
          GuiUtils.invokeLaterIfNeeded(Runnable {
            val entry = toolWindowManager.idToEntry.get(id) ?: return@Runnable
            val windowInfo = entry.readOnlyWindowInfo
            if (!windowInfo.isVisible) {
              return@Runnable
            }

            toolWindowManager.activateToolWindow(entry, toolWindowManager.getRegisteredMutableInfoOrLogError(entry.id), autoFocusContents = false)
          }, ModalityState.defaultModalityState(), toolWindowManager.project.disposed)
        })
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
        stripeButton.updateIcon(toolWindow.icon)
      }
      else {
        stripeButton.updatePresentation()
      }
    }
    ActivateToolWindowAction.updateToolWindowActionPresentation(toolWindow)
  }

  internal fun activated(toolWindow: ToolWindowImpl) {
    activateToolWindow(idToEntry.get(toolWindow.id)!!, getRegisteredMutableInfoOrLogError(toolWindow.id))
  }

  /**
   * Handles event from decorator and modify weight/floating bounds of the
   * tool window depending on decoration type.
   */
  @ApiStatus.Internal
  fun resized(source: InternalDecorator) {
    if (!source.isShowing) {
      // do not recalculate the tool window size if it is not yet shown (and, therefore, has 0,0,0,0 bounds)
      return
    }

    val toolWindow = source.toolWindow
    val info = getRegisteredMutableInfoOrLogError(toolWindow.id)
    if (info.type == ToolWindowType.FLOATING) {
      val owner = SwingUtilities.getWindowAncestor(source)
      if (owner != null) {
        info.floatingBounds = owner.bounds
      }
    }
    else if (info.type == ToolWindowType.WINDOWED) {
      val decorator = getWindowedDecorator(toolWindow.id)
      val frame = decorator?.getFrame()
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

      val layeredPane = toolWindowPane!!.layeredPane
      var paneWeight = if (anchor.isHorizontal) source.height.toFloat() / layeredPane.height else source.width.toFloat() / layeredPane.width
      info.weight = paneWeight
      if (another != null && anchor.isSplitVertically) {
        paneWeight = if (anchor.isHorizontal) another.height.toFloat() / layeredPane.height else another.width.toFloat() / layeredPane.width
        getRegisteredMutableInfoOrLogError(another.toolWindow.id).weight = paneWeight
      }
    }
  }

  private fun focusToolWindowByDefault() {
    var toFocus: ToolWindowEntry? = null
    for (each in activeStack.stack) {
      if (each.readOnlyWindowInfo.isVisible) {
        toFocus = each
        break
      }
    }

    if (toFocus == null) {
      for (each in activeStack.persistentStack) {
        if (each.readOnlyWindowInfo.isVisible) {
          toFocus = each
          break
        }
      }
    }

    if (toFocus != null && !ApplicationManager.getApplication().isDisposed) {
      activateToolWindow(toFocus, getRegisteredMutableInfoOrLogError(toFocus.id))
    }
  }

  fun setShowStripeButton(id: String, visibleOnPanel: Boolean) {
    val info = getRegisteredMutableInfoOrLogError(id)
    if (visibleOnPanel == info.isShowStripeButton) {
      return
    }

    info.isShowStripeButton = visibleOnPanel
    idToEntry.get(info.id!!)!!.applyWindowInfo(info.copy())
    fireStateChanged()
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
    for (entry in idToEntry.values) {
      val info = layout.getInfo(entry.id) ?: continue
      if (!info.isVisible) {
        continue
      }

      if (info.type == ToolWindowType.FLOATING) {
        if (entry.floatingDecorator == null) {
          violations.add("Floating window has no decorator: ${entry.id}")
        }
      }
      else if (info.type == ToolWindowType.WINDOWED) {
        if (entry.windowedDecorator == null) {
          violations.add("Windowed window has no decorator: ${entry.id}")
        }
      }
    }

    if (violations.isNotEmpty()) {
      LOG.error("Invariants failed: \n${violations.joinToString("\n")}\nContext: $additionalMessage")
    }
  }
}

private enum class KeyState {
  WAITING, PRESSED, RELEASED, HOLD
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

    if (Registry.`is`("toolwindow.disable.overlay.by.double.key")) {
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
private const val LAYOUT_TO_RESTORE = "layout-to-restore"

internal enum class ToolWindowProperty {
  TITLE, ICON, AVAILABLE, STRIPE_TITLE
}

private fun isInActiveToolWindow(component: Any?, activeToolWindow: ToolWindowImpl): Boolean {
  var source = if (component is JComponent) component else null
  val activeToolWindowComponent = activeToolWindow.getComponentIfInitialized()
  if (activeToolWindowComponent != null) {
    while (source != null && source !== activeToolWindowComponent) {
      source = if (source.parent != null && source.parent is JComponent) source.parent as JComponent else null
    }
  }
  return source != null
}

private fun getStripeTitleSupplier(id: String, pluginDescriptor: PluginDescriptor): Supplier<String>? {
  val classLoader = pluginDescriptor.pluginClassLoader
  val bundleName = when (pluginDescriptor.pluginId) {
    PluginManagerCore.CORE_ID -> IdeBundle.BUNDLE
    else -> pluginDescriptor.resourceBundleBaseName ?: return null
  }

  try {
    val bundle = DynamicBundle.INSTANCE.getResourceBundle(bundleName, classLoader)
    val key = "toolwindow.stripe.$id".replace(" ", "_")
    val label = BundleBase.messageOrDefault(bundle, key, id)
    return Supplier { label }
  }
  catch (e: MissingResourceException) {
    LOG.warn("Missing bundle $bundleName at $classLoader", e)
  }
  return null
}

private fun findIconFromBean(bean: ToolWindowEP, factory: ToolWindowFactory): Icon? {
  IconLoader.findIcon(bean.icon ?: return null, factory.javaClass)?.let {
    return it
  }

  try {
    return IconLoader.getIcon(bean.icon)
  }
  catch (ignored: Exception) {
    return EmptyIcon.ICON_13
  }
}

private fun addStripeButton(button: StripeButton, stripe: Stripe) {
  stripe.addButton(button) { o1, o2 -> windowInfoComparator.compare(o1.windowInfo, o2.windowInfo) }
}

private inline fun processDescriptors(crossinline handler: (bean: ToolWindowEP, pluginDescriptor: PluginDescriptor) -> Unit) {
  ToolWindowEP.EP_NAME.processWithPluginDescriptor { bean, pluginDescriptor ->
    try {
      handler(bean, pluginDescriptor)
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.error("Cannot process toolwindow ${bean.id}", e)
    }
  }
}

private fun removeStripeButton(button: StripeButton) {
  (button.parent as? Stripe)?.removeButton(button)
}