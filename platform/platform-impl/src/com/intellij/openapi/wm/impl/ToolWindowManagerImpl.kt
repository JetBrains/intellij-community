// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "OverridingDeprecatedMember", "ReplaceNegatedIsEmptyWithIsNotEmpty",
               "PrivatePropertyName")
@file:OptIn(FlowPreview::class)

package com.intellij.openapi.wm.impl

import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.PluginException
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.UiActivity
import com.intellij.ide.UiActivityMonitor
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.actions.MaximizeActiveDialogAction
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.ui.UISettings
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ToolWindowCollector
import com.intellij.notification.impl.NotificationsManagerImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.MnemonicHelper
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.Logger
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
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.ThreeComponentsSplitter
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.serviceContainer.NonInjectable
import com.intellij.toolWindow.*
import com.intellij.ui.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.*
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.SimpleMessageBusConnection
import com.intellij.util.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeListener
import java.lang.Runnable
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<ToolWindowManagerImpl>()
private val performShowInSeparateTask = System.getProperty("idea.toolwindow.show.separate.task", "false").toBoolean()

private typealias Mutation = ((WindowInfoImpl) -> Unit)

@ApiStatus.Internal
open class ToolWindowManagerImpl @NonInjectable @TestOnly internal constructor(
  val project: Project,
  @field:JvmField internal val isNewUi: Boolean,
  private val isEdtRequired: Boolean,
  @JvmField internal val coroutineScope: CoroutineScope,
) : ToolWindowManagerEx(), Disposable {
  private val dispatcher = EventDispatcher.create(ToolWindowManagerListener::class.java)

  private val state: ToolWindowManagerState by lazy(LazyThreadSafetyMode.NONE) { project.service() }

  var layoutState: DesktopLayout
    get() = state.layout
    set(value) {
      state.layout = value
    }

  private val idToEntry = ConcurrentHashMap<String, ToolWindowEntry>()
  private val activeStack = ActiveStack()
  private val sideStack = SideStack()
  private val toolWindowPanes = LinkedHashMap<String, ToolWindowPane>()

  private var projectFrame: JFrame?
    get() = state.projectFrame
    set(value) {
      state.projectFrame = value
    }

  override var layoutToRestoreLater: DesktopLayout?
    get() = state.layoutToRestoreLater
    set(value) {
      state.layoutToRestoreLater = value
    }

  private var currentState = KeyState.WAITING
  private val waiterForSecondPress: SingleAlarm?
  private val recentToolWindowsState: LinkedList<String>
    get() = state.recentToolWindows

  @Suppress("LeakingThis")
  private val toolWindowSetInitializer = ToolWindowSetInitializer(project, this)

  @Suppress("TestOnlyProblems")
  constructor(project: Project, coroutineScope: CoroutineScope)
    : this(project, isNewUi = ExperimentalUI.isNewUI(), isEdtRequired = true, coroutineScope = coroutineScope)

  init {
    if (project.isDefault) {
      waiterForSecondPress = null
    }
    else {
      waiterForSecondPress = SingleAlarm.singleEdtAlarm(
        task = {
          if (currentState != KeyState.HOLD) {
            resetHoldState()
          }
        },
        delay = SystemProperties.getIntProperty("actionSystem.keyGestureDblClickTime", 300),
        coroutineScope = coroutineScope,
      )
      if (state.noStateLoaded) {
        loadDefault()
      }
      @Suppress("LeakingThis")
      state.scheduledLayout.afterChange(this) { dl ->
        dl?.let { toolWindowSetInitializer.scheduleSetLayout(it) }
      }
      state.scheduledLayout.get()?.let { toolWindowSetInitializer.scheduleSetLayout(it) }
    }
  }

  companion object {
    /**
     * Setting this [client property][JComponent.putClientProperty] allows specifying 'effective' parent for a component which will be used
     * to find a tool window to which component belongs (if any). This can prevent tool windows in non-default view modes (e.g. 'Undock')
     * to close when focus is transferred to a component not in tool window hierarchy, but logically belonging to it (e.g. when component
     * is added to the window's layered pane).
     *
     * @see ComponentUtil.putClientProperty
     */
    @JvmField
    val PARENT_COMPONENT: Key<JComponent> = Key.create("tool.window.parent.component")

    @JvmStatic
    @ApiStatus.Internal
    fun getRegisteredMutableInfoOrLogError(decorator: InternalDecoratorImpl): WindowInfoImpl {
      val toolWindow = decorator.toolWindow
      return toolWindow.toolWindowManager.getRegisteredMutableInfoOrLogError(toolWindow.id)
    }

    fun getAdjustedRatio(partSize: Int, totalSize: Int, direction: Int): Float {
      var ratio = partSize.toFloat() / totalSize
      ratio += (((partSize.toFloat() + direction) / totalSize) - ratio) / 2
      return ratio
    }
  }

  fun isToolWindowRegistered(id: String): Boolean = idToEntry.containsKey(id)

  internal fun getEntry(id: String): ToolWindowEntry? = idToEntry.get(id)

  internal fun assertIsEdt() {
    if (isEdtRequired) {
      EDT.assertIsEdt()
    }
  }

  override fun dispose() {
  }

  @Service(Service.Level.APP)
  internal class ToolWindowManagerAppLevelHelper(coroutineScope: CoroutineScope) {
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

          toolWindowManager.revalidateStripeButtons()

          val toolWindowId = getToolWindowIdForComponent(event.component) ?: return

          val activeEntry = toolWindowManager.idToEntry.get(toolWindowId) ?: return
          val windowInfo = activeEntry.readOnlyWindowInfo
          // just removed
          if (!windowInfo.isVisible) {
            return
          }

          if (!(windowInfo.isAutoHide || windowInfo.type == ToolWindowType.SLIDING)) {
            return
          }

          // let's check that tool window actually loses focus
          if (getToolWindowIdForComponent(event.oppositeComponent) != toolWindowId) {
            // a toolwindow lost focus
            val focusGoesToPopup = JBPopupFactory.getInstance().getParentBalloonFor(event.oppositeComponent) != null
            if (!focusGoesToPopup) {
              val info = toolWindowManager.getRegisteredMutableInfoOrLogError(toolWindowId)
              toolWindowManager.deactivateToolWindow(info, activeEntry)
            }
          }
        }
        else if (event.id == FocusEvent.FOCUS_GAINED) {
          val component = event.component ?: return
          for (project in getOpenedProjects()) {
            for (composite in (FileEditorManagerEx.getInstanceExIfCreated(project) ?: return).activeSplittersComposites) {
              if (composite.allEditors.any { SwingUtilities.isDescendingFrom(component, it.component) }) {
                (getInstance(project) as ToolWindowManagerImpl).activeStack.clear()
              }
            }
          }
        }
      }

      private inline fun process(processor: (manager: ToolWindowManagerImpl) -> Unit) {
        for (project in getOpenedProjects()) {
          processor(getInstance(project) as ToolWindowManagerImpl)
        }
      }
    }

    private class MyListener : AWTEventListener {
      override fun eventDispatched(event: AWTEvent?) {
        if (event is FocusEvent) {
          handleFocusEvent(event)
        }
        else if (event is WindowEvent && event.getID() == WindowEvent.WINDOW_LOST_FOCUS) {
          process { manager ->
            val frame = event.getSource() as? JFrame
            // Reset the hold state if a tool window owning frame is losing focus, and the window gaining focus isn't a tool window frame
            if (manager.toolWindowPanes.values.any { it.frame === frame }
                && manager.toolWindowPanes.values.all { it.frame !== event.oppositeWindow }) {
              manager.resetHoldState()
            }
          }
        }
      }
    }

    init {
      val awtFocusListener = MyListener()
      Toolkit.getDefaultToolkit().addAWTEventListener(awtFocusListener, AWTEvent.FOCUS_EVENT_MASK or AWTEvent.WINDOW_FOCUS_EVENT_MASK)

      val updateHeadersRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
      coroutineScope.launch {
        updateHeadersRequests
          .debounce(50)
          .collectLatest {
            for (project in getOpenedProjects()) {
              val toolWindowManager = project.serviceAsync<ToolWindowManager>() as ToolWindowManagerImpl
              withContext(Dispatchers.EDT) {
                toolWindowManager.updateToolWindowHeaders()
              }
            }
          }
      }
      val focusListener = PropertyChangeListener { check(updateHeadersRequests.tryEmit(Unit)) }
      FocusUtil.addFocusOwnerListener(ApplicationManager.getApplication(), focusListener)

      val connection = ApplicationManager.getApplication().messageBus.connect(coroutineScope)
      connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
        override fun projectClosingBeforeSave(project: Project) {
          val manager = (project.serviceIfCreated<ToolWindowManager>() as ToolWindowManagerImpl?) ?: return
          for (entry in manager.idToEntry.values) {
            manager.saveFloatingOrWindowedState(entry, manager.layoutState.getInfo(entry.id) ?: continue)
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
              it.stripeButton?.updatePresentation()
            }
          }
        }
      })

      connection.subscribe(AnActionListener.TOPIC, object : AnActionListener {
        override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
          process { manager ->
            if (manager.currentState != KeyState.HOLD) {
              manager.resetHoldState()
            }
          }

          if (ExperimentalUI.isNewUI()) {
            if (event.place == ActionPlaces.TOOLWINDOW_TITLE) {
              val toolWindowManager = getInstance(event.project!!) as ToolWindowManagerImpl
              val toolWindowId = event.dataContext.getData(PlatformDataKeys.TOOL_WINDOW)?.id ?: return
              toolWindowManager.activateToolWindow(toolWindowId, null, true)
            }

            if (event.place == ActionPlaces.TOOLWINDOW_POPUP) {
              val toolWindowManager = getInstance(event.project!!) as ToolWindowManagerImpl
              val activeEntry = toolWindowManager.idToEntry.get(toolWindowManager.lastActiveToolWindowId ?: return) ?: return
              (activeEntry.toolWindow.decorator ?: return).headerToolbar.component.isVisible = true
            }
          }
        }
      })

      IdeEventQueue.getInstance().addDispatcher({ event ->
                                                  if (event is KeyEvent) {
                                                    process { manager ->
                                                      manager.dispatchKeyEvent(event)
                                                    }
                                                  }

                                                  false
                                                }, coroutineScope)
    }
  }

  private fun getDefaultToolWindowPane() = toolWindowPanes.get(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)!!

  internal fun getToolWindowPane(paneId: String): ToolWindowPane = toolWindowPanes.get(paneId) ?: getDefaultToolWindowPane()

  internal fun getToolWindowPane(toolWindow: ToolWindow): ToolWindowPane {
    val paneId = if (toolWindow is ToolWindowImpl) {
      toolWindow.windowInfo.safeToolWindowPaneId
    }
    else {
      idToEntry.get(toolWindow.id)?.readOnlyWindowInfo?.safeToolWindowPaneId ?: WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
    }
    return getToolWindowPane(paneId)
  }

  @VisibleForTesting
  internal open fun getButtonManager(toolWindow: ToolWindow): ToolWindowButtonManager = getToolWindowPane(toolWindow).buttonManager

  internal fun addToolWindowPane(toolWindowPane: ToolWindowPane, parentDisposable: Disposable) {
    toolWindowPanes.put(toolWindowPane.paneId, toolWindowPane)
    Disposer.register(parentDisposable) {
      for (it in idToEntry.values) {
        if (it.readOnlyWindowInfo.safeToolWindowPaneId == toolWindowPane.paneId) {
          hideToolWindow(id = it.id,
                         hideSide = false,
                         moveFocus = false,
                         removeFromStripe = true,
                         source = ToolWindowEventSource.CloseAction)
        }
      }
      toolWindowPanes.remove(toolWindowPane.paneId)
    }
  }

  internal fun getToolWindowPanes(): List<ToolWindowPane> = toolWindowPanes.values.toList()

  private fun revalidateStripeButtons() {
    val buttonManagers = toolWindowPanes.values.mapNotNull { it.buttonManager as? ToolWindowPaneNewButtonManager }
    ApplicationManager.getApplication().invokeLater(ContextAwareRunnable { buttonManagers.forEach { it.refreshUi() } }, project.disposed)
  }

  internal fun createNotInHierarchyIterable(paneId: String): Iterable<Component> {
    return Iterable {
      idToEntry.values.asSequence().mapNotNull {
        if (getToolWindowPane(it.toolWindow).paneId == paneId) {
          val component = it.toolWindow.decoratorComponent
          if (component != null && component.parent == null) component else null
        }
        else null
      }.iterator()
    }
  }

  private fun updateToolWindowHeaders() {
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(ExpirableRunnable.forProject(project) {
      for (entry in idToEntry.values) {
        if (entry.readOnlyWindowInfo.isVisible) {
          val decorator = entry.toolWindow.decorator ?: continue
          decorator.repaint()
          decorator.updateActiveAndHoverState()
        }
      }
      revalidateStripeButtons()
    })
  }

  @Suppress("DEPRECATION")
  private fun dispatchKeyEvent(e: KeyEvent) {
    if ((e.keyCode != KeyEvent.VK_CONTROL) && (
        e.keyCode != KeyEvent.VK_ALT) && (e.keyCode != KeyEvent.VK_SHIFT) && (e.keyCode != KeyEvent.VK_META)) {
      if (e.modifiers == 0) {
        resetHoldState()
      }
      return
    }

    if (e.id != KeyEvent.KEY_PRESSED && e.id != KeyEvent.KEY_RELEASED) {
      return
    }

    val parent = e.component?.let { ComponentUtil.findUltimateParent(it) }
    if (parent is IdeFrame) {
      if ((parent as IdeFrame).project !== project) {
        resetHoldState()
        return
      }
    }

    val vks = getActivateToolWindowVKsMask()
    if (vks == 0) {
      resetHoldState()
      return
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
  }

  private fun resetHoldState() {
    currentState = KeyState.WAITING
    toolWindowPanes.values.forEach { it.setStripesOverlaid(value = false) }
  }

  private fun processState(pressed: Boolean) {
    if (pressed) {
      if (currentState == KeyState.WAITING) {
        currentState = KeyState.PRESSED
      }
      else if (currentState == KeyState.RELEASED) {
        currentState = KeyState.HOLD
        if (!AdvancedSettings.getBoolean("ide.suppress.double.click.handler")) {
          toolWindowPanes.values.forEach { it.setStripesOverlaid(value = true) }
        }
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

  internal suspend fun init(
    pane: ToolWindowPane,
    reopeningEditorJob: Job,
    taskListDeferred: Deferred<List<RegisterToolWindowTask>>,
  ) {
    doInit(pane = pane,
           connection = project.messageBus.connect(coroutineScope),
           reopeningEditorJob = reopeningEditorJob,
           taskListDeferred = taskListDeferred
    )
  }

  @Internal
  @VisibleForTesting
  suspend fun doInit(
    pane: ToolWindowPane,
    connection: SimpleMessageBusConnection,
    reopeningEditorJob: Job,
    taskListDeferred: Deferred<List<RegisterToolWindowTask>>?,
  ) {
    withContext(ModalityState.any().asContextElement()) {
      launch(Dispatchers.EDT) {
        this@ToolWindowManagerImpl.projectFrame = pane.frame

        // Make sure we haven't already created the root tool window pane.
        // We might have created panes for secondary frames, as they get
        // registered differently, but we shouldn't have the main pane yet
        LOG.assertTrue(!toolWindowPanes.containsKey(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID))

        // This will be the tool window pane for the default frame, which is not automatically added by the ToolWindowPane constructor.
        // If we're reopening other frames, their tool window panes will be added,
        // but we still need to initialise the tool windows themselves.
        toolWindowPanes.put(pane.paneId, pane)
      }
      connection.subscribe(ToolWindowManagerListener.TOPIC, dispatcher.multicaster)
      toolWindowSetInitializer.initUi(reopeningEditorJob, taskListDeferred)
    }

    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        coroutineScope.launch(Dispatchers.EDT) {
          focusManager.doWhenFocusSettlesDown(ExpirableRunnable.forProject(project) {
            if (!FileEditorManager.getInstance(project).hasOpenFiles()) {
              focusToolWindowByDefault()
            }
          })
        }
      }
    })
  }

  @Deprecated("Use {{@link #registerToolWindow(RegisterToolWindowTask)}}")
  fun initToolWindow(bean: ToolWindowEP) {
    ThreadingAssertions.assertEventDispatchThread()
    runWithModalProgressBlocking(project, "") {
      initToolWindow(bean, bean.pluginDescriptor)
    }
  }

  suspend fun initToolWindow(bean: ToolWindowEP, plugin: PluginDescriptor) {
    val condition = bean.getCondition(plugin)
    if (condition != null && !condition.value(project)) {
      return
    }

    val factory = bean.getToolWindowFactory(bean.pluginDescriptor)
    if (!factory.isApplicableAsync(project)) {
      return
    }

    withContext(Dispatchers.EDT) {
      // always add to the default tool window pane
      @Suppress("DEPRECATION")
      val task = RegisterToolWindowTask(
        id = bean.id,
        icon = findIconFromBean(bean, factory, plugin),
        anchor = getToolWindowAnchor(factory, bean),
        sideTool = bean.secondary || bean.side,
        canCloseContent = bean.canCloseContents,
        shouldBeAvailable = factory.shouldBeAvailable(project),
        contentFactory = factory,
        stripeTitle = getStripeTitleSupplier(id = bean.id, project = project, pluginDescriptor = plugin)
      ).apply {
        pluginDescriptor = plugin
      }

      val toolWindowPane = getDefaultToolWindowPaneIfInitialized()
      registerToolWindow(task, toolWindowPane.buttonManager)

      toolWindowPane.buttonManager.getStripeFor(task.anchor, task.sideTool).revalidate()
      toolWindowPane.validate()
      toolWindowPane.repaint()
    }
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowsRegistered(listOf(bean.id), this)
  }

  private fun getDefaultToolWindowPaneIfInitialized(): ToolWindowPane {
    return toolWindowPanes.get(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)
           ?: throw IllegalStateException("You must not register toolwindow programmatically so early. " +
                                          "Rework code or use ToolWindowManager.invokeLater")
  }

  internal fun projectClosed() {
    // hide everything outside the frame (floating and windowed) - frame contents are handled separately elsewhere
    for (entry in idToEntry.values) {
      if (entry.toolWindow.windowInfo.type.isInternal) {
        continue
      }

      try {
        removeExternalDecorators(entry)
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
  }

  private fun loadDefault() {
    toolWindowSetInitializer.scheduleSetLayout(ToolWindowDefaultLayoutManager.getInstance().getLayoutCopy())
  }

  @Deprecated("Use {@link ToolWindowManagerListener#TOPIC}", level = DeprecationLevel.ERROR)
  override fun addToolWindowManagerListener(listener: ToolWindowManagerListener) {
    dispatcher.addListener(listener)
  }

  override fun activateEditorComponent() {
    EditorsSplitters.focusDefaultComponentInSplittersIfPresent(project)
  }

  @RequiresEdt
  open fun activateToolWindow(id: String, runnable: Runnable?, autoFocusContents: Boolean, source: ToolWindowEventSource? = null) {
    val activity = UiActivity.Focus("toolWindow:$id")
    UiActivityMonitor.getInstance().addActivity(project, activity, ModalityState.nonModal())

    activateToolWindow(entry = idToEntry.get(id)!!,
                       info = getRegisteredMutableInfoOrLogError(id),
                       autoFocusContents = autoFocusContents,
                       source = source)

    coroutineScope.launch(Dispatchers.EDT) {
      //maybe readaction
      writeIntentReadAction {
        runnable?.run()
        UiActivityMonitor.getInstance().removeActivity(project, activity)
      }
    }
  }

  internal fun activateToolWindow(
    entry: ToolWindowEntry,
    info: WindowInfoImpl,
    autoFocusContents: Boolean = true,
    source: ToolWindowEventSource? = null,
  ) {
    LOG.debug { "activateToolWindow($entry)" }

    if (isUnifiedToolWindowSizesEnabled()) {
      info.weight = layoutState.getUnifiedAnchorWeight(info.anchor)
      LOG.debug("Activated tool window: ${info.id}, using ${info.anchor} unified weight of ${info.weight}")
    }

    if (source != null) {
      ToolWindowCollector.getInstance().recordActivation(project, entry.id, info, source)
    }

    recentToolWindowsState.remove(entry.id)
    recentToolWindowsState.add(0, entry.id)

    if (!entry.toolWindow.isAvailable) {
      // The Tool window can be "logically" active but not focused.
      // For example, when the user switched to another application.
      // So we just need to bring a tool window to the front.
      if (autoFocusContents && !entry.toolWindow.hasFocus) {
        entry.toolWindow.requestFocusInToolWindow()
      }

      return
    }

    if (!entry.readOnlyWindowInfo.isVisible) {
      info.isActiveOnStart = autoFocusContents
      showToolWindowImpl(entry, info, dirtyMode = false, source = source)
    }
    else if (!autoFocusContents /* if focus is requested, focusing code will do this */ && !info.type.isInternal) {
      bringOwnerToFront(entry.toolWindow, false)
    }

    if (autoFocusContents && ApplicationManager.getApplication().isActive) {
      entry.toolWindow.requestFocusInToolWindow()
    }
    else {
      activeStack.push(entry)
    }

    fireStateChanged(ToolWindowManagerEventType.ActivateToolWindow, entry.toolWindow)
  }

  private fun isUnifiedToolWindowSizesEnabled(): Boolean = !isIndependentToolWindowResizeEnabled()

  private fun isIndependentToolWindowResizeEnabled(): Boolean {
    return if (isNewUi) {
      UISettings.getInstance().rememberSizeForEachToolWindowNewUI
    }
    else {
      UISettings.getInstance().rememberSizeForEachToolWindowOldUI
    }
  }

  private fun visibleToolWindow(anchor: ToolWindowAnchor): ToolWindowEntry? {
    return idToEntry.values.firstOrNull { it.isVisibleAndDockedTo(anchor) }
  }

  private fun ToolWindowEntry.isVisibleAndDockedTo(anchor: ToolWindowAnchor): Boolean {
    return toolWindow.isVisible && readOnlyWindowInfo.isDocked && readOnlyWindowInfo.anchor == anchor
  }

  private val ToolWindowEntry.weight get() = readOnlyWindowInfo.weight

  fun getRecentToolWindows(): List<String> = java.util.List.copyOf(recentToolWindowsState)

  internal fun updateToolWindow(toolWindow: ToolWindowImpl, component: Component) {
    toolWindow.setFocusedComponent(component)
    if (toolWindow.isAvailable && !toolWindow.isActive) {
      activateToolWindow(toolWindow.id, null, autoFocusContents = false)
    }
    activeStack.push(idToEntry.get(toolWindow.id) ?: return)
    toolWindow.decorator?.headerToolbar?.component?.isVisible = true
  }

  // mutate operation must use info from layout and not from decorator
  internal fun getRegisteredMutableInfoOrLogError(id: String): WindowInfoImpl {
    var info = layoutState.getInfo(id)
    if (info == null) {
      val entry = idToEntry.get(id) ?: throw IllegalStateException("window with id=\"$id\" isn't registered")
      // window was registered but stripe button was not shown, so, layout was not added to a list
      info = (entry.readOnlyWindowInfo as WindowInfoImpl).copy()
      layoutState.addInfo(id, info)
    }
    return info
  }

  private fun deactivateToolWindow(
    info: WindowInfoImpl,
    entry: ToolWindowEntry,
    dirtyMode: Boolean = false,
    mutation: Mutation? = null,
    source: ToolWindowEventSource? = null,
  ) {
    LOG.debug { "deactivateToolWindow(${info.id})" }

    setHiddenState(info, entry, source)
    mutation?.invoke(info)
    updateStateAndRemoveDecorator(info, entry, dirtyMode = dirtyMode)

    entry.applyWindowInfo(info.copy())
  }

  private fun setHiddenState(info: WindowInfoImpl, entry: ToolWindowEntry, source: ToolWindowEventSource?) {
    ToolWindowCollector.getInstance().recordHidden(project, info, source)

    info.isActiveOnStart = false
    info.isVisible = false
    activeStack.remove(entry, false)
  }

  override val toolWindowIds: Array<String>
    get() = idToEntry.keys.toTypedArray()

  override val toolWindows: List<ToolWindow>
    get() = idToEntry.values.map { it.toolWindow }

  override val toolWindowIdSet: Set<String>
    get() = java.util.Set.copyOf(idToEntry.keys)

  override val activeToolWindowId: String?
    get() {
      val frame = toolWindowPanes.values.firstOrNull { it.frame.isActive }?.frame ?: projectFrame ?: return null
      if (frame.isActive) {
        return getToolWindowIdForComponent(frame.mostRecentFocusOwner)
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
    get() = getLastActiveToolWindows().firstOrNull()?.id

  internal fun getLastActiveToolWindows(): Sequence<ToolWindow> {
    EDT.assertIsEdt()
    return (0 until activeStack.persistentSize).asSequence()
      .map { activeStack.peekPersistent(it).toolWindow }
      .filter { it.isAvailable }
  }

  override fun isStripeButtonShow(toolWindow: ToolWindow): Boolean = idToEntry.get(toolWindow.id)?.stripeButton != null

  /**
   * @return windowed decorator for the tool window with specified `ID`.
   */
  private fun getWindowedDecorator(id: String) = idToEntry.get(id)?.windowedDecorator

  override fun getIdsOn(anchor: ToolWindowAnchor): List<String> = getVisibleToolWindowsOn(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID,
                                                                                          anchor).map { it.id }.toList()

  internal fun getToolWindowsOn(paneId: String, anchor: ToolWindowAnchor, excludedId: String): MutableList<ToolWindowEx> {
    return getVisibleToolWindowsOn(paneId, anchor)
      .filter { it.id != excludedId }
      .map { it.toolWindow }
      .toMutableList()
  }

  internal fun getDockedInfoAt(paneId: String, anchor: ToolWindowAnchor?, side: Boolean): WindowInfo? {
    return idToEntry.values.asSequence()
      .map { it.readOnlyWindowInfo }
      .find { it.isVisible && it.isDocked && it.safeToolWindowPaneId == paneId && it.anchor == anchor && it.isSplit == side }
  }

  override fun getLocationIcon(id: String, fallbackIcon: Icon): Icon {
    val info = layoutState.getInfo(id) ?: return fallbackIcon
    val type = info.type
    if (type == ToolWindowType.FLOATING || type == ToolWindowType.WINDOWED) {
      return AllIcons.Actions.MoveToWindow
    }

    val anchor = info.anchor
    val splitMode = info.isSplit
    return when (anchor) {
      ToolWindowAnchor.BOTTOM -> if (splitMode) AllIcons.Actions.MoveToBottomRight else AllIcons.Actions.MoveToBottomLeft
      ToolWindowAnchor.LEFT -> if (splitMode) AllIcons.Actions.MoveToLeftBottom else AllIcons.Actions.MoveToLeftTop
      ToolWindowAnchor.RIGHT -> if (splitMode) AllIcons.Actions.MoveToRightBottom else AllIcons.Actions.MoveToRightTop
      ToolWindowAnchor.TOP -> if (splitMode) AllIcons.Actions.MoveToTopRight else AllIcons.Actions.MoveToTopLeft
      else -> fallbackIcon
    }
  }

  private fun getVisibleToolWindowsOn(paneId: String, anchor: ToolWindowAnchor): Sequence<ToolWindowEntry> {
    return idToEntry.values
      .asSequence()
      .filter { it.toolWindow.isAvailable && it.readOnlyWindowInfo.safeToolWindowPaneId == paneId && it.readOnlyWindowInfo.anchor == anchor }
  }

  // cannot be ToolWindowEx because of backward compatibility
  override fun getToolWindow(id: String?): ToolWindow? {
    return idToEntry.get(id ?: return null)?.toolWindow
  }

  open fun showToolWindow(id: String) {
    LOG.debug { "showToolWindow($id)" }
    EDT.assertIsEdt()
    val entry = idToEntry.get(id) ?: throw IllegalThreadStateException("window with id=\"$id\" is not registered")
    if (entry.readOnlyWindowInfo.isVisible) {
      LOG.assertTrue(entry.toolWindow.getComponentIfInitialized() != null)
      return
    }

    val info = getRegisteredMutableInfoOrLogError(id)
    if (showToolWindowImpl(entry, info, dirtyMode = false)) {
      checkInvariants(id)
      fireStateChanged(ToolWindowManagerEventType.ShowToolWindow, entry.toolWindow)
    }
  }

  override fun hideToolWindow(id: String, hideSide: Boolean) {
    hideToolWindow(id = id, hideSide = hideSide, source = null)
  }

  open fun hideToolWindow(
    id: String,
    hideSide: Boolean = false,
    moveFocus: Boolean = true,
    removeFromStripe: Boolean = false,
    source: ToolWindowEventSource? = null,
  ) {
    EDT.assertIsEdt()

    val entry = idToEntry.get(id)!!
    var moveFocusAfter = moveFocus && entry.toolWindow.isActive
    if (!entry.readOnlyWindowInfo.isVisible) {
      moveFocusAfter = false
    }

    val info = getRegisteredMutableInfoOrLogError(id)
    val mutation: Mutation? = if (removeFromStripe) {
      {
        info.isShowStripeButton = false
        entry.removeStripeButton()
      }
    }
    else {
      null
    }
    executeHide(entry, info, dirtyMode = false, hideSide = hideSide, mutation = mutation, source = source)
    if (removeFromStripe) {
      // If we're removing the stripe, reset to the root pane ID. Note that the current value is used during hide
      info.toolWindowPaneId = WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
    }
    fireStateChanged(ToolWindowManagerEventType.HideToolWindow, entry.toolWindow)
    if (moveFocusAfter) {
      activateEditorComponent()
    }
    revalidateStripeButtons()
  }

  private fun executeHide(
    entry: ToolWindowEntry,
    info: WindowInfoImpl,
    dirtyMode: Boolean,
    hideSide: Boolean = false,
    mutation: Mutation? = null,
    source: ToolWindowEventSource? = null,
  ) {
    // hide and deactivate
    deactivateToolWindow(info, entry, dirtyMode = dirtyMode, mutation = mutation, source = source)

    if (hideSide && info.type != ToolWindowType.FLOATING && info.type != ToolWindowType.WINDOWED) {
      for (each in getVisibleToolWindowsOn(info.safeToolWindowPaneId, info.anchor)) {
        activeStack.remove(each, false)
      }
      if (isStackEnabled) {
        while (!sideStack.isEmpty(info.anchor)) {
          sideStack.pop(info.anchor)
        }
      }
      for (otherEntry in idToEntry.values) {
        val otherInfo = layoutState.getInfo(otherEntry.id) ?: continue
        if (otherInfo.isVisible && otherInfo.safeToolWindowPaneId == info.safeToolWindowPaneId && otherInfo.anchor == info.anchor) {
          deactivateToolWindow(otherInfo, otherEntry, dirtyMode = dirtyMode, source = ToolWindowEventSource.HideSide)
        }
      }
    }
    else {
      // first, we have to find a tool window that was located at the same side and was hidden
      if (isStackEnabled) {
        var info2: WindowInfoImpl? = null
        while (!sideStack.isEmpty(info.anchor)) {
          val storedInfo = sideStack.pop(info.anchor)
          if (storedInfo.isSplit != info.isSplit) {
            continue
          }

          val currentInfo = getRegisteredMutableInfoOrLogError(storedInfo.id!!)
          // SideStack contains copies of real WindowInfos. It means that
          // these stored info can be invalid. The following loop removes invalid WindowInfos.
          if (storedInfo.safeToolWindowPaneId == currentInfo.safeToolWindowPaneId && storedInfo.anchor == currentInfo.anchor
              && storedInfo.type == currentInfo.type && storedInfo.isAutoHide == currentInfo.isAutoHide) {
            info2 = storedInfo
            break
          }
        }
        if (info2 != null) {
          val entry2 = idToEntry[info2.id!!]!!
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
  private fun showToolWindowImpl(
    entry: ToolWindowEntry,
    toBeShownInfo: WindowInfoImpl,
    dirtyMode: Boolean,
    source: ToolWindowEventSource? = null,
  ): Boolean {
    if (!entry.toolWindow.isAvailable) {
      return false
    }

    ToolWindowCollector.getInstance().recordShown(project, source, toBeShownInfo)
    toBeShownInfo.isVisible = true
    toBeShownInfo.isShowStripeButton = true
    if (toBeShownInfo.order == -1) {
      toBeShownInfo.order = layoutState.getMaxOrder(toBeShownInfo.safeToolWindowPaneId, toBeShownInfo.anchor)
    }

    val snapshotInfo = toBeShownInfo.copy()
    entry.applyWindowInfo(snapshotInfo)
    doShowWindow(entry, snapshotInfo, dirtyMode)

    return true
  }

  private fun doShowWindow(entry: ToolWindowEntry, info: WindowInfo, dirtyMode: Boolean) {
    if (LOG.isDebugEnabled) {
      LOG.debug("Showing the tool window ${info.id}")
    }
    if (entry.readOnlyWindowInfo.type == ToolWindowType.FLOATING) {
      addFloatingDecorator(entry, info)
    }
    else if (entry.readOnlyWindowInfo.type == ToolWindowType.WINDOWED) {
      addWindowedDecorator(entry, info)
    }
    else {
      // docked and sliding windows
      // If there is a tool window on the same side, then we have to hide it, i.e.,
      // clear place for a tool window to be shown.
      //
      // We store WindowInfo of a hidden tool window in the SideStack (if the tool window
      // is docked and not auto-hide one). Therefore, it's possible to restore the
      // hidden tool window when showing a tool window will be closed.
      for (otherEntry in idToEntry.values) {
        if (entry.id == otherEntry.id) {
          continue
        }

        val otherInfo = otherEntry.readOnlyWindowInfo
        if (otherInfo.isVisible && otherInfo.type == info.type && otherInfo.isSplit == info.isSplit
            && otherInfo.safeToolWindowPaneId == info.safeToolWindowPaneId && otherInfo.anchor == info.anchor) {
          val otherLayoutInto = layoutState.getInfo(otherEntry.id)!!
          // hide and deactivate tool window
          setHiddenState(otherLayoutInto, otherEntry, ToolWindowEventSource.HideOnShowOther)

          val otherInfoCopy = otherLayoutInto.copy()
          otherEntry.applyWindowInfo(otherInfoCopy)
          otherEntry.toolWindow.decoratorComponent?.let { decorator ->
            val toolWindowPane = getToolWindowPane(otherInfoCopy.safeToolWindowPaneId)
            toolWindowPane.removeDecorator(otherInfoCopy, decorator, false, this)
          }

          // store WindowInfo into the SideStack
          if (isStackEnabled && otherInfo.isDocked && !otherInfo.isAutoHide) {
            sideStack.push(otherInfoCopy)
          }
        }
      }

      // This check is for testability. The tests don't create UI, so there are no actual panes
      if (toolWindowPanes.containsKey(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)) {
        val toolWindowPane = getToolWindowPane(info.safeToolWindowPaneId)
        toolWindowPane.addDecorator(entry.toolWindow.getOrCreateDecoratorComponent(), info, dirtyMode, this)
      }

      // remove a tool window from the SideStack
      if (isStackEnabled) {
        sideStack.remove(entry.id)
      }
    }

    if (entry.stripeButton == null) {
      val buttonManager = getButtonManager(entry.toolWindow)
      entry.stripeButton = buttonManager.createStripeButton(entry.toolWindow, info, task = null)
    }

    entry.toolWindow.scheduleContentInitializationIfNeeded()
    fireToolWindowShown(entry.toolWindow)
  }

  override fun registerToolWindow(task: RegisterToolWindowTask): ToolWindow {
    ThreadingAssertions.assertEventDispatchThread()

    // try to get a previously saved tool window pane, if possible
    val toolWindowPane = layoutState.getInfo(task.id)?.toolWindowPaneId?.let { getToolWindowPane(it) }
                         ?: getDefaultToolWindowPaneIfInitialized()
    val entry = registerToolWindow(task, buttonManager = toolWindowPane.buttonManager)
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowsRegistered(listOf(entry.id), this)

    toolWindowPane.buttonManager.getStripeFor(entry.toolWindow.anchor, entry.toolWindow.isSplitMode).revalidate()

    toolWindowPane.validate()
    toolWindowPane.repaint()
    fireStateChanged(ToolWindowManagerEventType.RegisterToolWindow, entry.toolWindow)
    return entry.toolWindow
  }

  internal fun registerToolWindow(task: RegisterToolWindowTask, buttonManager: ToolWindowButtonManager): ToolWindowEntry {
    val layout = layoutState
    val existingInfo = layout.getInfo(task.id)
    val preparedTask = PreparedRegisterToolWindowTask(
      task = task,
      isButtonNeeded = isButtonNeeded(task, existingInfo, project.service<ToolWindowStripeManager>()),
      existingInfo = existingInfo,
      // not used by registerToolWindow - makes sense only for `registerToolWindows` in ToolWindowSetInitializer
      paneId = existingInfo?.safeToolWindowPaneId ?: WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID,
    )
    val result = registerToolWindow(preparedTask = preparedTask, buttonManager = buttonManager, layout = layout, ensureToolWindowActionRegistered = true)
    result.postTask?.invoke()
    return result.entry
  }

  internal fun registerToolWindow(
    preparedTask: PreparedRegisterToolWindowTask,
    buttonManager: ToolWindowButtonManager,
    layout: DesktopLayout,
    ensureToolWindowActionRegistered: Boolean,
  ): RegisterToolWindowResult {
    val task = preparedTask.task

    LOG.debug { "registerToolWindow($task)" }

    if (idToEntry.containsKey(task.id)) {
      throw IllegalArgumentException("window with id=\"${task.id}\" is already registered")
    }

    var info: WindowInfoImpl? = preparedTask.existingInfo
    // do not create layout for New UI - button is not created for toolwindow by default
    if (info == null) {
      info = layout.create(task)
      if (preparedTask.isButtonNeeded) {
        // we must allocate order - otherwise, on drag-n-drop, we cannot move some tool windows to the end
        // because sibling's order is equal to -1, so, always in the end
        info.order = layout.getMaxOrder(paneId = info.safeToolWindowPaneId, anchor = task.anchor)
        layout.addInfo(task.id, info)
      }
    }

    val disposable = Disposer.newDisposable(task.id)
    Disposer.register(this, disposable)

    val factory = task.contentFactory

    val infoSnapshot = info.copy()
    if (infoSnapshot.isVisible && (factory == null || !task.shouldBeAvailable)) {
      // isVisible cannot be true if contentFactory is null, because we cannot show toolwindow without content
      infoSnapshot.isVisible = false
    }

    val toolWindow = ToolWindowImpl(toolWindowManager = this,
                                    id = task.id,
                                    canCloseContent = task.canCloseContent,
                                    dumbAware = task.canWorkInDumbMode,
                                    component = task.component,
                                    parentDisposable = disposable,
                                    windowInfo = infoSnapshot,
                                    contentFactory = factory,
                                    isAvailable = task.shouldBeAvailable,
                                    stripeTitleProvider = task.stripeTitle ?: Supplier { task.id } )
    if (task.hideOnEmptyContent) {
      toolWindow.setToHideOnEmptyContent(true)
    }

    if (factory != null) {
      toolWindow.windowInfoDuringInit = infoSnapshot
      try {
        factory.init(toolWindow)
      }
      catch (e: IllegalStateException) {
        LOG.error(PluginException(e, task.pluginDescriptor?.pluginId))
      }
      finally {
        toolWindow.windowInfoDuringInit = null
      }

      coroutineScope.launch {
        factory.manage(toolWindow = toolWindow, toolWindowManager = this@ToolWindowManagerImpl)
      }.cancelOnDispose(toolWindow.disposable)
    }

    // contentFactory.init can set icon
    if (toolWindow.icon == null) {
      task.icon?.let {
        toolWindow.doSetIcon(it)
      }
    }

    if (ensureToolWindowActionRegistered) {
      ActivateToolWindowAction.Manager.ensureToolWindowActionRegistered(toolWindow, ActionManager.getInstance())
    }

    val stripeButton = if (preparedTask.isButtonNeeded) {
      buttonManager.createStripeButton(toolWindow = toolWindow, info = infoSnapshot, task = task)
    }
    else {
      LOG.debug {
        "Button is not created for `${task.id}`" +
        "(isShowStripeButton: ${info.isShowStripeButton}, isAvailable: ${task.shouldBeAvailable})"
      }
      null
    }

    val entry = ToolWindowEntry(stripeButton = stripeButton, toolWindow = toolWindow, disposable = disposable)
    idToEntry.put(task.id, entry)

    // If preloaded info is visible or active, then we have to show/activate the installed
    // tool window. This step has sense only for windows which are not in the auto hide
    // mode. But if a tool window was active but its mode doesn't allow to activate it again
    // (for example, a tool window is in auto hide mode), then we just activate an editor component.
    if (stripeButton != null && factory != null /* not null on an init tool window from EP */ && infoSnapshot.isVisible) {
      val postTask = {
        showToolWindowImpl(entry = entry, toBeShownInfo = info, dirtyMode = false)

        // do not activate a tool window that is the part of the project frame - default component should be focused
        if (infoSnapshot.isActiveOnStart &&
            (infoSnapshot.type == ToolWindowType.WINDOWED || infoSnapshot.type == ToolWindowType.FLOATING) &&
            ApplicationManager.getApplication().isActive) {
          entry.toolWindow.requestFocusInToolWindow()
        }
      }
      if (performShowInSeparateTask) {
        return RegisterToolWindowResult(entry = entry, postTask = postTask)
      }
      else {
        postTask()
      }
    }

    return RegisterToolWindowResult(entry = entry, postTask = null)
  }

  internal fun isButtonNeeded(task: RegisterToolWindowTask, info: WindowInfoImpl?, stripeManager: ToolWindowStripeManager): Boolean {
    return (task.shouldBeAvailable
            && (info?.isShowStripeButton ?: !(isNewUi && isToolwindowOfBundledPlugin(task)))
            && stripeManager.allowToShowOnStripe(task.id, info == null, isNewUi))
  }

  @Deprecated("Use ToolWindowFactory and toolWindow extension point")
  @Suppress("OverridingDeprecatedMember")
  override fun unregisterToolWindow(id: String) {
    val toolWindow = idToEntry.get(id)?.toolWindow
    doUnregisterToolWindow(id)
    fireStateChanged(ToolWindowManagerEventType.UnregisterToolWindow, toolWindow)
  }

  private fun doUnregisterToolWindow(id: String) {
    LOG.debug { "unregisterToolWindow($id)" }

    ThreadingAssertions.assertEventDispatchThread()
    ActivateToolWindowAction.Manager.unregister(id)

    val entry = idToEntry.remove(id) ?: return
    val toolWindow = entry.toolWindow

    val info = layoutState.getInfo(id)
    if (info != null) {
      // remove decorator and tool button from the screen - removing will also save current bounds
      updateStateAndRemoveDecorator(info, entry, false)
      // save recent appearance of a tool window
      activeStack.remove(entry, true)
      if (isStackEnabled) {
        sideStack.remove(id)
      }
      entry.removeStripeButton()
      val toolWindowPane = getToolWindowPane(info.safeToolWindowPaneId)
      toolWindowPane.validate()
      toolWindowPane.repaint()
    }

    if (!project.isDisposed) {
      project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowUnregistered(id, (toolWindow))
    }

    Disposer.dispose(entry.disposable)
  }

  private fun updateStateAndRemoveDecorator(state: WindowInfoImpl, entry: ToolWindowEntry, dirtyMode: Boolean) {
    saveFloatingOrWindowedState(entry, state)
    removeDecoratorWithoutUpdatingState(entry, state, dirtyMode)
  }

  private fun removeDecoratorWithoutUpdatingState(entry: ToolWindowEntry, state: WindowInfoImpl, dirtyMode: Boolean) {
    removeExternalDecorators(entry)
    removeInternalDecorator(entry, state, dirtyMode)
  }

  private fun removeExternalDecorators(entry: ToolWindowEntry) {
    entry.windowedDecorator?.let {
      entry.windowedDecorator = null
      Disposer.dispose(it)
      detachInternalDecorator(entry)
      return
    }

    entry.floatingDecorator?.let {
      entry.floatingDecorator = null
      it.dispose()
      detachInternalDecorator(entry)
      return
    }
  }

  // This is important for RD/CWM case, when we might want to keep the content 'showing' by attaching it to ShowingContainer.
  private fun detachInternalDecorator(entry: ToolWindowEntry) {
    entry.toolWindow.decoratorComponent?.let { it.parent?.remove(it) }
  }

  private fun removeInternalDecorator(entry: ToolWindowEntry, state: WindowInfoImpl, dirtyMode: Boolean) {
    entry.toolWindow.decoratorComponent?.let {
      val toolWindowPane = getToolWindowPane(state.safeToolWindowPaneId)
      toolWindowPane.removeDecorator(state, it, dirtyMode, this)
      return
    }
  }

  private fun saveFloatingOrWindowedState(entry: ToolWindowEntry, info: WindowInfoImpl) {
    entry.floatingDecorator?.let {
      info.floatingBounds = it.visibleWindowBounds
      info.isActiveOnStart = it.isActive
      return
    }

    entry.windowedDecorator?.let { windowedDecorator ->
      info.isActiveOnStart = windowedDecorator.isActive
      val frame = windowedDecorator.getFrame()
      val externalDecorator = entry.externalDecorator
      if (frame.isShowing && externalDecorator != null) {
        val maximized = (frame as JFrame).extendedState == Frame.MAXIMIZED_BOTH
        if (maximized) {
          frame.extendedState = Frame.NORMAL
          frame.invalidate()
          frame.revalidate()
        }

        info.floatingBounds = externalDecorator.visibleWindowBounds
        info.isMaximized = maximized
      }
      return
    }
  }

  override fun getLayout(): DesktopLayout {
    ThreadingAssertions.assertEventDispatchThread()
    return layoutState
  }

  @VisibleForTesting
  fun setLayoutOnInit(newLayout: DesktopLayout) {
    if (!idToEntry.isEmpty()) {
      LOG.error("idToEntry must be empty (idToEntry={\n${idToEntry.entries.joinToString(separator = "\n") { (k, v) -> "$k: $v" }})")
    }
    if (LOG.isDebugEnabled) {
      LOG.debug("The initial tool window layout is $newLayout")
    }
    layoutState = newLayout
  }

  override fun setLayout(newLayout: DesktopLayout) {
    ThreadingAssertions.assertEventDispatchThread()
    if (LOG.isDebugEnabled) {
      LOG.debug("Restoring layout $newLayout")
    }

    if (idToEntry.isEmpty()) {
      LOG.debug("The current layout is empty, not applying anything")
      layoutState = newLayout
      return
    }

    val factoryDefault = ToolWindowDefaultLayoutManager.getInstance().getFactoryDefaultLayoutCopy()

    data class LayoutData(val old: WindowInfoImpl, val new: WindowInfoImpl, val entry: ToolWindowEntry)

    val list = mutableListOf<LayoutData>()

    for (entry in idToEntry.values) {
      val old = layoutState.getInfo(entry.id) ?: entry.readOnlyWindowInfo as WindowInfoImpl
      var new = newLayout.getInfo(entry.id)
      if (new == null) {
        // The window wasn't present when the layout was saved. The best thing we can do is to restore
        // its state to default. But if it doesn't even present in the default layout, there's little we can
        // do except at least make the window invisible (otherwise it'll stick around for no reason, see IDEA-321129),
        // and also remove its stripe button for similar reasons (IDEA-331827).
        new = factoryDefault.getInfo(entry.id)
        if (new == null) {
          new = old.copy().apply {
            isVisible = false
            isShowStripeButton = false
          }
          if (LOG.isDebugEnabled) {
            LOG.debug("The window ${old.id} doesn't exist in the new layout, making it and its stripe button invisible, " +
                      "its new state will be $new")
          }
        }
        else {
          if (LOG.isDebugEnabled) {
            LOG.debug("The window ${old.id} doesn't exist in the new layout, resetting it to the default, " +
                      "its new state will be $new")
          }
        }
        newLayout.addInfo(entry.id, new)
      }
      if (old != new) {
        if (!entry.toolWindow.isAvailable) {
          new.isVisible = false // Preserve invariants: if it can't be shown, don't consider it visible.
          if (LOG.isDebugEnabled) {
            LOG.debug("The window ${old.id} isn't currently available, making it invisible, its new state will be $new")
          }
        }
        list.add(LayoutData(old = old, new = new, entry = entry))
      }
    }

    this.layoutState = newLayout

    if (list.isEmpty()) {
      LOG.debug("No layout changes detected, nothing to do")
      return
    }

    LOG.debug("PASS 1: show/hide/dock/undock/rearrange")
    // Now, show/hide/dock/undock/rearrange tool windows and their stripe buttons.
    val iterator = list.iterator()
    while (iterator.hasNext()) {
      val item = iterator.next()

      // Apply the new info to all windows, including unavailable ones
      // (so they're shown in their appropriate places if they ARE shown later by some user action).
      item.entry.applyWindowInfo(item.new)

      // Then remove unavailable tool windows to exclude them from further processing.
      if (!item.entry.toolWindow.isAvailable) {
        iterator.remove()
        continue
      }

      if (item.old.isVisible && !item.new.isVisible) {
        if (LOG.isDebugEnabled) {
          LOG.debug("Hiding the tool window ${item.entry.id} as it's invisible in the new layout")
        }
        updateStateAndRemoveDecorator(item.new, item.entry, dirtyMode = true)
      }

      if (item.old.safeToolWindowPaneId != item.new.safeToolWindowPaneId
          || item.old.anchor != item.new.anchor
          || item.old.order != item.new.order
          || item.old.isShowStripeButton != item.new.isShowStripeButton
      ) {
        if (LOG.isDebugEnabled) {
          LOG.debug("Updating the anchor of the tool window ${item.entry.id} because one of the following has changed:" +
                    " pane (old=${item.old.safeToolWindowPaneId} new=${item.new.safeToolWindowPaneId})," +
                    " anchor (old=${item.old.anchor} new=${item.new.anchor})," +
                    " order (old=${item.old.order} new=${item.new.order})," +
                    " isShowStripeButton (old=${item.old.isShowStripeButton} new=${item.new.isShowStripeButton})"
          )
        }
        setToolWindowAnchorImpl(item.entry, item.old, item.new, item.new.safeToolWindowPaneId, item.new.anchor, item.new.order, null)
      }

      var toShowWindow = false

      if (item.old.isSplit != item.new.isSplit && item.old.type.isInternal && item.new.type.isInternal) {
        val wasVisible = item.old.isVisible
        // we should hide the window and show it in a 'new place'
        // to automatically hide a possible window that is already located in a 'new place'
        if (wasVisible) {
          if (LOG.isDebugEnabled) {
            LOG.debug("Temporarily hiding the tool window ${item.entry.id} because one of the following is changed:" +
                      " isSplit (old=${item.old.isSplit} new=${item.new.isSplit})," +
                      " isInternal (old=${item.old.type.isInternal} new=${item.new.type.isInternal})"
            )
          }
          hideToolWindow(item.entry.id)
          toShowWindow = true
        }
      }

      if (item.old.type != item.new.type) {
        val dirtyMode = item.old.type.isInternal
        if (LOG.isDebugEnabled) {
          LOG.debug("Saving the tool window ${item.entry.id} state and removing its decorators " +
                    "as its type has changed: ${item.old.type} -> ${item.new.type}, " +
                    "will ${if (item.new.isVisible) "re-show" else "NOT re-show"} it again later"
          )
        }
        updateStateAndRemoveDecorator(item.old, item.entry, dirtyMode)
        if (item.new.isVisible) {
          toShowWindow = true
        }
      }
      else if (!item.old.isVisible && item.new.isVisible) {
        if (LOG.isDebugEnabled) {
          LOG.debug("The tool window ${item.entry.id} will become visible in the new layout")
        }
        toShowWindow = true
      }

      if (toShowWindow) {
        doShowWindow(item.entry, item.new, dirtyMode = true)
      }
    }

    LOG.debug("PASS 2: adjust sizes")
    // Now that the windows are shown/hidden/docked/whatever, we can adjust their sizes properly:
    for (item in list) {
      if (item.new.isVisible && item.new.isDocked) {
        val toolWindowPane = getToolWindowPane(item.entry.toolWindow)
        if (item.old.weight != item.new.weight) {
          if (LOG.isDebugEnabled) {
            LOG.debug("Changing the tool window ${item.entry.id} weight from ${item.old.weight} to ${item.new.weight}")
          }
          val anchor = item.new.anchor
          var weight = item.new.weight
          val another = list.firstOrNull { it !== item && it.new.anchor == anchor && it.new.isVisible && it.new.isDocked }
          if (another != null && anchor.isUltrawideLayout()) { // split windows side-by-side, set weight of the entire splitter
            weight += another.new.weight
            if (LOG.isDebugEnabled) {
              LOG.debug("Adding to the tool window ${item.entry.id} weight " +
                        "the weight of ${another.entry.id} (${another.new.weight}) " +
                        "because they're displayed side-by-side in the ultrawide layout"
              )
            }
          }
          LOG.debug("Setting ${item.entry.id} weight=${weight} from the saved layout")
          toolWindowPane.setWeight(anchor, weight)
        }
        if (item.old.sideWeight != item.new.sideWeight) {
          LOG.debug("Setting ${item.entry.id} side weight ${item.new.sideWeight} from the saved layout")
          toolWindowPane.setSideWeight(item.entry.toolWindow, item.new.sideWeight)
        }
      }
    }

    LOG.debug("PASS 3: revalidate/repaint panes")
    val rootPanes = HashSet<JRootPane>()
    list.forEach { layoutData ->
      getToolWindowPane(layoutData.entry.toolWindow).let { toolWindowPane ->
        toolWindowPane.buttonManager.revalidateNotEmptyStripes()
        toolWindowPane.validate()
        toolWindowPane.repaint()
        toolWindowPane.frame.rootPane?.let { rootPanes.add(it) }
      }
    }

    activateEditorComponent()

    rootPanes.forEach {
      it.revalidate()
      it.repaint()
    }

    fireStateChanged(ToolWindowManagerEventType.SetLayout)

    checkInvariants(null)
  }

  override fun getMoreButtonSide(): ToolWindowAnchor = state.moreButton

  override fun setMoreButtonSide(side: ToolWindowAnchor) {
    state.moreButton = side

    if (isNewUi) {
      for (pane in toolWindowPanes.values) {
        val buttonManager = pane.buttonManager
        if (buttonManager is ToolWindowPaneNewButtonManager) {
          buttonManager.updateMoreButtons()
        }
      }
    }

    fireStateChanged(ToolWindowManagerEventType.MoreButtonUpdated)
  }

  override fun setShowNames(value: Boolean) {
    if (isNewUi) {
      for (pane in toolWindowPanes.values) {
        val buttonManager = pane.buttonManager
        if (buttonManager is ToolWindowPaneNewButtonManager) {
          buttonManager.updateResizeState(null)
        }
      }
    }

    fireStateChanged(ToolWindowManagerEventType.ShowNames)
  }

  override fun setSideCustomWidth(toolbar: ToolWindowToolbar, width: Int) {
    if (isNewUi) {
      for (pane in toolWindowPanes.values) {
        val buttonManager = pane.buttonManager
        if (buttonManager is ToolWindowPaneNewButtonManager) {
          buttonManager.updateResizeState(toolbar)
        }
      }
    }

    fireStateChanged(ToolWindowManagerEventType.SideCustomWidth)
  }

  override fun invokeLater(runnable: Runnable) {
    if (!toolWindowSetInitializer.addToPendingTasksIfNotInitialized(runnable)) {
      coroutineScope.launch(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
        runnable.run()
      }
    }
  }

  override val focusManager: IdeFocusManager
    get() = IdeFocusManager.getGlobalInstance()

  override fun canShowNotification(toolWindowId: String): Boolean {
    val readOnlyWindowInfo = idToEntry.get(toolWindowId)?.readOnlyWindowInfo
    val anchor = readOnlyWindowInfo?.anchor ?: return false
    val isSplit = readOnlyWindowInfo.isSplit
    return toolWindowPanes.values.firstNotNullOfOrNull { it.buttonManager.getStripeFor(anchor, isSplit).getButtonFor(toolWindowId) } != null
  }

  override fun notifyByBalloon(options: ToolWindowBalloonShowOptions) {
    if (isNewUi) {
      notifySquareButtonByBalloon(options)
      return
    }

    val entry = idToEntry.get(options.toolWindowId)!!
    entry.balloon?.let {
      Disposer.dispose(it)
    }

    val toolWindowPane = getToolWindowPane(entry.toolWindow)
    val stripe = toolWindowPane.buttonManager.getStripeFor(entry.readOnlyWindowInfo.anchor, entry.readOnlyWindowInfo.isSplit)
    if (!entry.toolWindow.isAvailable) {
      entry.toolWindow.isPlaceholderMode = true
      stripe.updatePresentation()
      stripe.revalidate()
      stripe.repaint()
    }

    val anchor = entry.readOnlyWindowInfo.anchor
    val position = Ref(Balloon.Position.below)
    when (anchor) {
      ToolWindowAnchor.TOP -> position.set(Balloon.Position.below)
      ToolWindowAnchor.BOTTOM -> position.set(Balloon.Position.above)
      ToolWindowAnchor.LEFT -> position.set(Balloon.Position.atRight)
      ToolWindowAnchor.RIGHT -> position.set(Balloon.Position.atLeft)
    }

    if (!entry.readOnlyWindowInfo.isVisible) {
      toolWindowAvailable(entry.toolWindow)
    }

    val balloon = createBalloon(options, entry)
    val button = stripe.getButtonFor(options.toolWindowId)?.getComponent()

    val show = Runnable {
      val tracker: PositionTracker<Balloon>
      if (entry.toolWindow.isVisible &&
          (entry.toolWindow.type == ToolWindowType.WINDOWED ||
           entry.toolWindow.type == ToolWindowType.FLOATING)) {
        tracker = createPositionTracker(entry.toolWindow.component, ToolWindowAnchor.BOTTOM)
      }
      else if (button == null || !button.isShowing) {
        tracker = createPositionTracker(toolWindowPane, anchor)
      }
      else {
        tracker = object : PositionTracker<Balloon>(button) {
          override fun recalculateLocation(b: Balloon): RelativePoint? {
            val otherEntry = idToEntry.get(options.toolWindowId) ?: return null
            val stripeButton = otherEntry.stripeButton
            if (stripeButton == null || otherEntry.readOnlyWindowInfo.anchor != anchor) {
              b.hide()
              return null
            }
            val component = stripeButton.getComponent()
            return RelativePoint(component, Point(component.bounds.width / 2, component.height / 2 - 2))
          }
        }
      }
      if (!balloon.isDisposed) {
        balloon.show(tracker, position.get())
      }
    }

    if (button != null && button.isValid) {
      show.run()
    }
    else {
      SwingUtilities.invokeLater(show)
    }
  }

  private fun notifySquareButtonByBalloon(options: ToolWindowBalloonShowOptions) {
    val entry = idToEntry.get(options.toolWindowId)!!
    entry.balloon?.let(Disposer::dispose)

    val anchor = entry.readOnlyWindowInfo.anchor
    var position = when (anchor) {
      ToolWindowAnchor.TOP -> Balloon.Position.atRight
      ToolWindowAnchor.RIGHT -> Balloon.Position.atRight
      ToolWindowAnchor.BOTTOM -> Balloon.Position.atLeft
      ToolWindowAnchor.LEFT -> Balloon.Position.atLeft
      else -> Balloon.Position.atLeft
    }

    val balloon = createBalloon(options, entry)
    val toolWindowPane = getToolWindowPane(entry.readOnlyWindowInfo.safeToolWindowPaneId)
    val buttonManager = toolWindowPane.buttonManager as ToolWindowPaneNewButtonManager
    var button = buttonManager.getSquareStripeFor(entry.readOnlyWindowInfo.anchor).getButtonFor(options.toolWindowId)?.getComponent()
    if (button == null && entry.readOnlyWindowInfo.anchor == ToolWindowAnchor.BOTTOM) {
      button = buttonManager.getSquareStripeFor(ToolWindowAnchor.RIGHT).getButtonFor(options.toolWindowId)?.getComponent()
      if (button != null && button.isShowing) {
        position = Balloon.Position.atRight
      }
    }
    if (button == null || !button.isShowing) {
      button = buttonManager.getMoreButton(getMoreButtonSide())
      position = Balloon.Position.atLeft
    }
    val show = Runnable {
      val tracker: PositionTracker<Balloon>
      if (entry.toolWindow.isVisible &&
          (entry.toolWindow.type == ToolWindowType.WINDOWED ||
           entry.toolWindow.type == ToolWindowType.FLOATING)) {
        tracker = createPositionTracker(entry.toolWindow.component, ToolWindowAnchor.BOTTOM)
      }
      else if (!button.isShowing) {
        tracker = createPositionTracker(toolWindowPane, anchor)
        if (balloon is BalloonImpl) {
          balloon.setShowPointer(false)
        }
      }
      else {
        tracker = object : PositionTracker<Balloon>(button) {
          override fun recalculateLocation(balloon: Balloon): RelativePoint? {
            val otherEntry = idToEntry.get(options.toolWindowId) ?: return null
            if (otherEntry.readOnlyWindowInfo.anchor != anchor) {
              balloon.hide()
              return null
            }

            return RelativePoint(button,
                                 Point(if (position == Balloon.Position.atRight) 0 else button.bounds.width, button.height / 2))
          }
        }
      }
      if (!balloon.isDisposed) {
        balloon.show(tracker, position)
      }
    }

    if (button.isValid) {
      show.run()
    }
    else {
      SwingUtilities.invokeLater(show)
    }
  }

  private fun createPositionTracker(component: Component, anchor: ToolWindowAnchor): PositionTracker<Balloon> {
    return object : PositionTracker<Balloon>(component) {
      override fun recalculateLocation(balloon: Balloon): RelativePoint {
        val bounds = component.bounds
        val target = StartupUiUtil.getCenterPoint(bounds, Dimension(1, 1))
        when (anchor) {
          ToolWindowAnchor.TOP -> target.y = 0
          ToolWindowAnchor.BOTTOM -> target.y = bounds.height - 3
          ToolWindowAnchor.LEFT -> target.x = 0
          ToolWindowAnchor.RIGHT -> target.x = bounds.width
        }
        return RelativePoint(component, target)
      }
    }
  }

  private fun createBalloon(options: ToolWindowBalloonShowOptions, entry: ToolWindowEntry): Balloon {
    val listenerWrapper = BalloonHyperlinkListener(options.listener)

    var foreground = options.type.titleForeground
    var background = options.type.popupBackground
    var borderColor = options.type.borderColor
    if (isNewUi && options.type === MessageType.INFO) {
      foreground = HintHint.Status.Info.foreground
      background = HintHint.Status.Info.background
      borderColor = HintHint.Status.Info.border
    }

    val content = options.htmlBody.replace("\n", "<br>")
    val balloonBuilder = JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(content, options.icon, foreground, background, listenerWrapper)
      .setBorderColor(borderColor)
      .setHideOnClickOutside(true)
      .setHideOnFrameResize(false)

    options.balloonCustomizer?.accept(balloonBuilder)

    if (isNewUi) {
      balloonBuilder.setBorderInsets(JBUI.insets(9, 7, 11, 7)).setPointerSize(JBUI.size(16, 8)).setPointerShiftedToStart(
        true).setCornerRadius(JBUI.scale(8))
    }

    val balloon = balloonBuilder.createBalloon()
    if (balloon is BalloonImpl) {
      balloon.setHideOnClickOutside(false)
      NotificationsManagerImpl.frameActivateBalloonListener(balloon) {
        coroutineScope.launch {
          delay(100.milliseconds)
          withContext(Dispatchers.EDT) {
            balloon.setHideOnClickOutside(true)
          }
        }
      }
    }

    listenerWrapper.balloon = balloon
    entry.balloon = balloon
    Disposer.register(balloon) {
      entry.toolWindow.isPlaceholderMode = false
      entry.balloon = null
    }
    Disposer.register(entry.disposable, balloon)
    return balloon
  }

  @RequiresEdt
  override fun closeBalloons() {
    for (entry in idToEntry.values) {
      entry.balloon?.hideImmediately()
    }
  }

  override fun getToolWindowBalloon(id: String): Balloon? = idToEntry.get(id)?.balloon

  override val isEditorComponentActive: Boolean
    get() = state.isEditorComponentActive

  @RequiresEdt
  fun setToolWindowAnchor(id: String, anchor: ToolWindowAnchor) {
    setToolWindowAnchor(id = id, anchor = anchor, order = -1)
  }

  // used by Rider
  @Suppress("MemberVisibilityCanBePrivate")
  fun setToolWindowAnchor(id: String, anchor: ToolWindowAnchor, order: Int) {
    val entry = idToEntry.get(id)!!

    val info = entry.readOnlyWindowInfo
    if (anchor == info.anchor && (order == info.order || order == -1)) {
      return
    }

    ThreadingAssertions.assertEventDispatchThread()
    setToolWindowAnchorImpl(entry, info, getRegisteredMutableInfoOrLogError(id), info.safeToolWindowPaneId, anchor, order, layoutState)
    getToolWindowPane(info.safeToolWindowPaneId).validateAndRepaint()
    fireStateChanged(ToolWindowManagerEventType.SetToolWindowAnchor, entry.toolWindow)
  }

  fun setVisibleOnLargeStripe(id: String, visible: Boolean) {
    val info = getRegisteredMutableInfoOrLogError(id)
    info.isShowStripeButton = visible
    val entry = idToEntry.get(id)!!
    entry.applyWindowInfo(info.copy())
    fireStateChanged(ToolWindowManagerEventType.SetVisibleOnLargeStripe, entry.toolWindow)
  }

  private fun setToolWindowAnchorImpl(
    entry: ToolWindowEntry,
    currentInfo: WindowInfo,
    layoutInfo: WindowInfoImpl,
    paneId: String,
    anchor: ToolWindowAnchor,
    order: Int,
    layoutState: DesktopLayout?,
  ) {
    // Get the current tool window pane, not the one we're aiming for
    val toolWindowPane = getToolWindowPane(currentInfo.safeToolWindowPaneId)

    // if a tool window isn't visible, or only order number is changed, then just remove/add stripe button
    if (!currentInfo.isVisible || (paneId == currentInfo.safeToolWindowPaneId && anchor == currentInfo.anchor) ||
        currentInfo.type == ToolWindowType.FLOATING || currentInfo.type == ToolWindowType.WINDOWED) {
      doSetAnchor(entry = entry,
                  info = layoutInfo,
                  paneId = paneId,
                  anchor = anchor,
                  order = order,
                  currentInfo = currentInfo,
                  layoutState = layoutState)
    }
    else {
      val wasFocused = entry.toolWindow.isActive
      // for docked and sliding windows, we have to move buttons and window's decorators
      layoutInfo.isVisible = false
      toolWindowPane.removeDecorator(info = currentInfo, component = entry.toolWindow.decoratorComponent, dirtyMode = true, manager = this)

      doSetAnchor(entry = entry,
                  info = layoutInfo,
                  paneId = paneId,
                  anchor = anchor,
                  order = order,
                  currentInfo = currentInfo,
                  layoutState = layoutState)

      showToolWindowImpl(entry, layoutInfo, false)
      if (wasFocused) {
        entry.toolWindow.requestFocusInToolWindow()
      }
    }
  }

  private fun doSetAnchor(
    entry: ToolWindowEntry,
    info: WindowInfoImpl,
    paneId: String,
    anchor: ToolWindowAnchor,
    order: Int,
    currentInfo: WindowInfo? = null,
    layoutState: DesktopLayout?,
  ) {
    if (isNewUi && currentInfo != null) {
      entry.removeStripeButton(currentInfo.anchor, currentInfo.isSplit)
    }
    else {
      entry.removeStripeButton()
    }

    if (layoutState != null) {
      if (LOG.isDebugEnabled) {
        LOG.debug("Updating order of the affected windows because ${entry.id} has changed anchor")
      }
      for (otherInfo in layoutState.setAnchor(info, paneId, anchor, order)) {
        val otherToolWindow = idToEntry.get(otherInfo.id ?: continue)?.toolWindow ?: continue
        if (LOG.isDebugEnabled) {
          LOG.debug("Affected tool window ${otherInfo.id}: ${windowInfoChanges(otherToolWindow.windowInfo, otherInfo)}")
        }
        otherToolWindow.setWindowInfoSilently(otherInfo.copy())
      }
    }

    entry.toolWindow.applyWindowInfo(info.copy())
    if (info.isShowStripeButton || info.isVisible) { // A safety check: if the tool window is visible, we ignore isShowStripeButton.
      entry.stripeButton = getToolWindowPane(paneId).buttonManager.createStripeButton(
        toolWindow = entry.toolWindow,
        info = info, task = null
      )
    }
  }

  internal fun setSideTool(id: String, isSplit: Boolean) {
    val entry = idToEntry.get(id)
    if (entry == null) {
      LOG.error("Cannot set side tool: toolwindow $id is not registered")
      return
    }

    if (entry.readOnlyWindowInfo.isSplit != isSplit) {
      setSideTool(entry, getRegisteredMutableInfoOrLogError(id), isSplit)
      fireStateChanged(ToolWindowManagerEventType.SetSideTool, entry.toolWindow)
    }
  }

  private fun setSideTool(entry: ToolWindowEntry, info: WindowInfoImpl, isSplit: Boolean) {
    if (isSplit == info.isSplit) {
      return
    }

    // we should hide the window and show it in a 'new place'
    // to automatically hide a possible window that is already located in a 'new place'
    hideIfNeededAndShowAfterTask(entry, info) {
      info.isSplit = isSplit
      for (otherEntry in idToEntry.values) {
        otherEntry.applyWindowInfo((layoutState.getInfo(otherEntry.id) ?: continue).copy())
      }
    }
    getToolWindowPane(info.safeToolWindowPaneId).buttonManager.getStripeFor(entry.readOnlyWindowInfo.anchor,
                                                                            entry.readOnlyWindowInfo.isSplit).revalidate()
  }

  fun setContentUiType(id: String, type: ToolWindowContentUiType) {
    val info = getRegisteredMutableInfoOrLogError(id)
    info.contentUiType = type
    val entry = idToEntry.get(id)!!
    entry.applyWindowInfo(info.copy())
    fireStateChanged(ToolWindowManagerEventType.SetContentUiType, entry.toolWindow)
  }

  internal fun setSideToolAndAnchor(id: String, paneId: String, anchor: ToolWindowAnchor, order: Int, isSplit: Boolean) {
    val entry = idToEntry.get(id)!!
    val readOnlyInfo = entry.readOnlyWindowInfo
    if (paneId == readOnlyInfo.safeToolWindowPaneId && anchor == readOnlyInfo.anchor
        && order == readOnlyInfo.order && readOnlyInfo.isSplit == isSplit) {
      return
    }

    val info = getRegisteredMutableInfoOrLogError(id)
    hideIfNeededAndShowAfterTask(entry, info) {
      info.isSplit = isSplit
      doSetAnchor(entry, info, paneId, anchor, order, null, layoutState)
    }
    fireStateChanged(ToolWindowManagerEventType.SetSideToolAndAnchor, entry.toolWindow)
  }

  private fun hideIfNeededAndShowAfterTask(
    entry: ToolWindowEntry,
    info: WindowInfoImpl,
    source: ToolWindowEventSource? = null,
    task: () -> Unit,
  ) {
    val wasVisible = entry.readOnlyWindowInfo.isVisible
    val wasFocused = entry.toolWindow.isActive
    if (wasVisible) {
      executeHide(entry, info, dirtyMode = true)
    }

    task()

    if (wasVisible) {
      ToolWindowCollector.getInstance().recordShown(project, source, info)
      info.isVisible = true
      val infoSnapshot = info.copy()
      entry.applyWindowInfo(infoSnapshot)
      doShowWindow(entry, infoSnapshot, dirtyMode = true)
      if (wasFocused) {
        getShowingComponentToRequestFocus(entry.toolWindow)?.requestFocusInWindow()
      }
    }

    getToolWindowPane(info.safeToolWindowPaneId).validateAndRepaint()
  }

  protected open fun fireStateChanged(changeType: ToolWindowManagerEventType, toolWindow: ToolWindow? = null) {
    val topic = project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC)
    if (toolWindow != null) {
      topic.stateChanged(this, toolWindow, changeType)
    }
    else {
      topic.stateChanged(this, changeType)
    }
  }

  private fun fireToolWindowShown(toolWindow: ToolWindow) {
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowShown(toolWindow)
  }

  internal fun setToolWindowAutoHide(id: String, autoHide: Boolean) {
    EDT.assertIsEdt()

    val info = getRegisteredMutableInfoOrLogError(id)
    if (info.isAutoHide == autoHide) {
      return
    }

    info.isAutoHide = autoHide
    val entry = idToEntry.get(id) ?: return

    val newInfo = info.copy()
    entry.applyWindowInfo(newInfo)

    fireStateChanged(ToolWindowManagerEventType.SetToolWindowAutoHide, entry.toolWindow)
  }

  fun setToolWindowType(id: String, type: ToolWindowType) {
    ThreadingAssertions.assertEventDispatchThread()

    val entry = idToEntry.get(id)!!
    if (entry.readOnlyWindowInfo.type == type) {
      return
    }

    setToolWindowTypeImpl(entry, getRegisteredMutableInfoOrLogError(entry.id), type)
    fireStateChanged(ToolWindowManagerEventType.SetToolWindowType, entry.toolWindow)
  }

  private fun setToolWindowTypeImpl(entry: ToolWindowEntry, info: WindowInfoImpl, type: ToolWindowType) {
    if (!entry.readOnlyWindowInfo.isVisible) {
      info.type = type
      entry.applyWindowInfo(info.copy())
      return
    }

    val dirtyMode = entry.readOnlyWindowInfo.type.isInternal
    updateStateAndRemoveDecorator(info, entry, dirtyMode)
    info.type = type
    if (type.isInternal) {
      info.internalType = type
    }

    val newInfo = info.copy()
    entry.applyWindowInfo(newInfo)
    doShowWindow(entry, newInfo, dirtyMode)

    if (ApplicationManager.getApplication().isActive) {
      entry.toolWindow.requestFocusInToolWindow()
    }

    val frame = getToolWindowPane(entry.toolWindow).frame
    val rootPane = frame.rootPane ?: return
    rootPane.revalidate()
    rootPane.repaint()
  }

  override fun clearSideStack() {
    if (isStackEnabled) {
      sideStack.clear()
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
    getToolWindowPane(toolWindow).stretchWidth(toolWindow, value)
  }

  override fun isMaximized(window: ToolWindow): Boolean = getToolWindowPane(window).isMaximized(window)

  override fun setMaximized(window: ToolWindow, maximized: Boolean) {
    if (window.type == ToolWindowType.FLOATING && window is ToolWindowImpl) {
      MaximizeActiveDialogAction.doMaximize(idToEntry.get(window.id)?.floatingDecorator)
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
    getToolWindowPane(window).setMaximized(window, maximized)
  }

  internal fun stretchHeight(toolWindow: ToolWindowImpl, value: Int) {
    getToolWindowPane(toolWindow).stretchHeight(toolWindow, value)
  }

  private fun addFloatingDecorator(entry: ToolWindowEntry, info: WindowInfo) {
    val frame = getToolWindowPane(entry.toolWindow).frame
    val decorator = entry.toolWindow.getOrCreateDecoratorComponent()
    val floatingDecorator = FloatingDecorator(frame, decorator)
    floatingDecorator.apply(info)

    entry.floatingDecorator = floatingDecorator
    setExternalDecoratorBounds(info, floatingDecorator, decorator, frame)

    floatingDecorator.show()
  }

  private fun addWindowedDecorator(entry: ToolWindowEntry, info: WindowInfo) {
    val app = ApplicationManager.getApplication()
    if (app.isHeadlessEnvironment || app.isUnitTestMode) {
      return
    }

    val id = entry.id
    val decorator = entry.toolWindow.getOrCreateDecoratorComponent()
    val windowedDecorator = WindowedDecorator(project, title = "${entry.toolWindow.stripeTitle} - ${project.name}", component = decorator)
    val window = windowedDecorator.getFrame()

    MnemonicHelper.init((window as RootPaneContainer).contentPane)

    val shouldBeMaximized = info.isMaximized
    setExternalDecoratorBounds(info, windowedDecorator, decorator, getToolWindowPane(entry.toolWindow).frame)
    entry.windowedDecorator = windowedDecorator
    Disposer.register(windowedDecorator) {
      if (idToEntry.get(id)?.windowedDecorator != null) {
        hideToolWindow(id, false)
      }
    }

    window.isAutoRequestFocus = info.isActiveOnStart
    try {
      windowedDecorator.show(false)
    }
    finally {
      window.isAutoRequestFocus = true
    }

    val rootPane = (window as RootPaneContainer).rootPane
    val rootPaneBounds = rootPane.bounds
    val point = rootPane.locationOnScreen
    val windowBounds = window.bounds
    if (LOG.isDebugEnabled) {
      LOG.debug("Adjusting the bounds of the windowed tool window ${info.id} according to " +
                "its bounds ($windowBounds) and its root pane bounds ($rootPaneBounds)")
    }
    window.setLocation(2 * windowBounds.x - point.x, 2 * windowBounds.y - point.y)
    window.setSize(2 * windowBounds.width - rootPaneBounds.width, 2 * windowBounds.height - rootPaneBounds.height)
    if (LOG.isDebugEnabled) {
      LOG.debug("The adjusted bounds are ${window.bounds}")
    }
    if (shouldBeMaximized && window is Frame) {
      window.extendedState = Frame.MAXIMIZED_BOTH
      LOG.debug("The window has also been maximized")
    }
  }

  private fun setExternalDecoratorBounds(
    info: WindowInfo,
    externalDecorator: ToolWindowExternalDecorator,
    internalDecorator: InternalDecoratorImpl,
    parentFrame: JFrame,
  ) {
    val storedBounds = info.floatingBounds
    val screen = ScreenUtil.getScreenRectangle(parentFrame)
    val needToCenter: Boolean
    val bounds: Rectangle
    if (storedBounds != null && isValidBounds(storedBounds)) {
      if (LOG.isDebugEnabled) {
        LOG.debug("Keeping the tool window ${info.id} valid bounds: $storedBounds")
      }
      bounds = Rectangle(storedBounds)
      needToCenter = false
    }
    else if (storedBounds != null && storedBounds.width > 0 && storedBounds.height > 0) {
      if (LOG.isDebugEnabled) {
        LOG.debug("Adjusting the stored bounds for the tool window ${info.id} to fit the screen $screen")
      }
      bounds = Rectangle(storedBounds)
      ScreenUtil.moveToFit(bounds, screen, null, true)
      if (LOG.isDebugEnabled) {
        LOG.debug("Adjusted the stored bounds to fit the screen: $bounds")
      }
      needToCenter = true
    }
    else {
      if (LOG.isDebugEnabled) {
        LOG.debug("Computing default bounds for the tool window ${info.id}")
      }
      // place a new frame at the center of the current frame if there are no floating bounds
      var size = internalDecorator.size
      if (size.width == 0 || size.height == 0) {
        val preferredSize = internalDecorator.preferredSize
        if (LOG.isDebugEnabled) {
          LOG.debug("Using the preferred size $preferredSize because the size $size is invalid")
        }
        size = preferredSize
      }
      bounds = Rectangle(externalDecorator.visibleWindowBounds.location, size)
      if (LOG.isDebugEnabled) {
        LOG.debug("Computed the bounds using the default location: $bounds")
      }
      needToCenter = true
    }
    externalDecorator.visibleWindowBounds = bounds
    if (needToCenter) {
      externalDecorator.setLocationRelativeTo(parentFrame)
      if (LOG.isDebugEnabled) {
        LOG.debug("Centered the bounds relative to the IDE frame: ${externalDecorator.visibleWindowBounds}")
      }
    }
  }

  private fun isValidBounds(bounds: Rectangle): Boolean {
    val topLeftVisible = ScreenUtil.isVisible(bounds.topLeft)
    val topRightVisible = ScreenUtil.isVisible(bounds.topRight)
    val mostlyVisible = ScreenUtil.isVisible(bounds)
    val isValid =
      bounds.width > 0 && bounds.height > 0 &&
      (topLeftVisible || topRightVisible) && // At least some part of the header must be visible,
      mostlyVisible // and that some sensible portion of the window is better be visible too.
    if (!isValid && LOG.isDebugEnabled) {
      LOG.debug("Not using saved bounds $bounds because they're invalid: " +
                "topLeftVisible=$topLeftVisible, topRightVisible=$topRightVisible mostlyVisible=$mostlyVisible")
    }
    return isValid
  }

  private val Rectangle.topLeft: Point get() = location
  private val Rectangle.topRight: Point get() = Point(x + width, y)

  internal fun toolWindowAvailable(toolWindow: ToolWindowImpl) {
    if (!toolWindow.isShowStripeButton) {
      return
    }

    val entry = idToEntry.get(toolWindow.id) ?: return
    if (entry.stripeButton == null) {
      val buttonManager = getButtonManager(entry.toolWindow)
      entry.stripeButton = buttonManager.createStripeButton(entry.toolWindow, entry.readOnlyWindowInfo, task = null)
    }

    val info = layoutState.getInfo(toolWindow.id)
    if (info != null && info.isVisible) {
      LOG.assertTrue(!entry.readOnlyWindowInfo.isVisible)
      showToolWindowImpl(entry = entry, toBeShownInfo = info, dirtyMode = false)
    }

    fireStateChanged(ToolWindowManagerEventType.ToolWindowAvailable, entry.toolWindow)
  }

  internal fun toolWindowUnavailable(toolWindow: ToolWindowImpl) {
    val entry = idToEntry.get(toolWindow.id)!!
    val moveFocusAfter = toolWindow.isActive && toolWindow.isVisible
    val info = getRegisteredMutableInfoOrLogError(toolWindow.id)
    executeHide(entry, info, dirtyMode = false, mutation = {
      entry.removeStripeButton()
    })
    fireStateChanged(ToolWindowManagerEventType.ToolWindowUnavailable, entry.toolWindow)
    if (moveFocusAfter) {
      activateEditorComponent()
    }
  }

  /**
   * Spies on IdeToolWindow properties and applies them to the window state.
   */
  open fun toolWindowPropertyChanged(toolWindow: ToolWindow, property: ToolWindowProperty) {
    val stripeButton = idToEntry.get(toolWindow.id)?.stripeButton
    if (stripeButton != null) {
      if (property == ToolWindowProperty.ICON) {
        stripeButton.updateIcon(toolWindow.icon)
      }
      else {
        stripeButton.updatePresentation()
      }
    }

    ActivateToolWindowAction.Manager.updateToolWindowActionPresentation(toolWindow)
  }

  internal fun activated(toolWindow: ToolWindowImpl, source: ToolWindowEventSource?) {
    val info = getRegisteredMutableInfoOrLogError(toolWindow.id)
    activateToolWindow(entry = idToEntry.get(toolWindow.id)!!, info = info, source = source)
  }

  /**
   * Handles event from decorator and modify the weight /floating bounds of the
   * tool window depending on a decoration type.
   */
  open fun movedOrResized(source: InternalDecoratorImpl) {
    if (!source.isShowing) {
      // do not recalculate the tool window size if it is not yet shown (and, therefore, has 0,0,0,0 bounds)
      return
    }

    val toolWindow = source.toolWindow
    val info = getRegisteredMutableInfoOrLogError(toolWindow.id)
    val externalDecorator = source.getExternalDecorator(info.type)
    val externalFloatingBounds = externalDecorator?.visibleWindowBounds
    if (info.type == ToolWindowType.FLOATING) {
      if (externalFloatingBounds != null) {
        info.floatingBounds = externalFloatingBounds
        if (LOG.isDebugEnabled) {
          LOG.debug("Floating tool window ${toolWindow.id} bounds updated: ${info.floatingBounds}")
        }
      }
    }
    else if (info.type == ToolWindowType.WINDOWED) {
      val decorator = getWindowedDecorator(toolWindow.id)
      val frame = decorator?.getFrame()
      if (frame == null || !frame.isShowing) {
        return
      }
      info.floatingBounds = externalFloatingBounds
      info.isMaximized = (frame as JFrame).extendedState == Frame.MAXIMIZED_BOTH
      if (LOG.isDebugEnabled) {
        LOG.debug("Windowed tool window ${toolWindow.id} bounds updated: ${info.floatingBounds}, maximized=${info.isMaximized}")
      }
    }
    else {
      // docked and sliding windows
      val dockingAreaComponent = if (source.parent is Splitter) source.parent as Splitter else source
      if (!dockingAreaComponentSizeCanBeTrusted(dockingAreaComponent)) {
        return
      }
      val anchor = info.anchor
      if (source.parent is Splitter) {
        var sizeInSplit = if (anchor.isSplitVertically) source.height else source.width
        val splitter = source.parent as Splitter
        if (splitter.secondComponent === source) {
          sizeInSplit += splitter.dividerWidth
        }
        info.sideWeight = getAdjustedRatio(partSize = sizeInSplit,
                                           totalSize = if (anchor.isSplitVertically) splitter.height else splitter.width,
                                           direction = if (splitter.secondComponent === source) -1 else 1)
      }
      val toolWindowPane = getToolWindowPane(toolWindow)
      val toolWindowWeight = getAdjustedWeight(toolWindowPane, anchor, source)
      val dockingAreaWeight = getAdjustedWeight(toolWindowPane, anchor, dockingAreaComponent)
      info.weight = toolWindowWeight
      layoutState.setUnifiedAnchorWeight(anchor, dockingAreaWeight)
      LOG.debug { "Moved/resized tool window ${info.id}, updated weight=${toolWindowWeight}, docking area weight=${dockingAreaWeight}" }
    }
    fireStateChanged(ToolWindowManagerEventType.MovedOrResized, toolWindow)
  }

  private fun dockingAreaComponentSizeCanBeTrusted(dockingAreaComponent: Component): Boolean {
    val parentSplitter = dockingAreaComponent.parent as? ThreeComponentsSplitter
    if (parentSplitter == null) {
      // The window is not in a splitter (e.g., View Mode = Undock),
      // so we don't have to worry about the splitter resizing it in a wrong way, like in IDEA-319836.
      return true
    }
    val editorComponent = parentSplitter.innerComponent
    if (editorComponent == null) {
      LOG.info("Editor area is null, not updating tool window weights")
      return false
    }
    if (!editorComponent.isVisible) {
      LOG.info("Editor area is not visible, not updating tool window weights")
      return false
    }
    return true
  }

  private fun getAdjustedWeight(
    toolWindowPane: ToolWindowPane,
    anchor: ToolWindowAnchor,
    component: Component,
  ): Float {
    val wholeSize = toolWindowPane.rootPane.size
    return getAdjustedRatio(
      partSize = if (anchor.isHorizontal) component.height else component.width,
      totalSize = if (anchor.isHorizontal) wholeSize.height else wholeSize.width,
      direction = 1
    )
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

  internal fun setShowStripeButton(id: String, value: Boolean) {
    if (isNewUi) {
      LOG.info("Ignore setShowStripeButton(id=$id, value=$value) - not applicable for a new UI")
      return
    }

    val entry = idToEntry.get(id) ?: throw IllegalStateException("window with id=\"$id\" isn't registered")
    var info = layoutState.getInfo(id)
    if (info == null) {
      if (!value) {
        return
      }

      // window was registered but stripe button was not shown, so, layout was not added to a list
      info = (entry.readOnlyWindowInfo as WindowInfoImpl).copy()
      layoutState.addInfo(id, info)
    }
    if (value == info.isShowStripeButton) {
      return
    }

    info.isShowStripeButton = value
    if (!value) {
      entry.removeStripeButton()
    }
    entry.applyWindowInfo(info.copy())
    if (value && entry.stripeButton == null) {
      val buttonManager = getButtonManager(entry.toolWindow)
      entry.stripeButton = buttonManager.createStripeButton(entry.toolWindow, entry.readOnlyWindowInfo, task = null)
    }
    fireStateChanged(ToolWindowManagerEventType.SetShowStripeButton, entry.toolWindow)
  }

  private fun checkInvariants(id: String?) {
    val app = ApplicationManager.getApplication()
    if (!app.isEAP && !app.isInternal) {
      return
    }

    val violations = mutableListOf<String>()
    for (entry in idToEntry.values) {
      val info = layoutState.getInfo(entry.id) ?: continue
      if (!info.isVisible) {
        continue
      }

      if (!app.isHeadlessEnvironment && !app.isUnitTestMode) {
        if (info.type == ToolWindowType.FLOATING) {
          if (entry.floatingDecorator == null) {
            violations.add("Floating window has no decorator: ${entry.id}")
          }
        }
        else if (info.type == ToolWindowType.WINDOWED && entry.windowedDecorator == null) {
          violations.add("Windowed window has no decorator: ${entry.id}")
        }
      }
    }

    if (violations.isNotEmpty()) {
      LOG.error("Invariants failed: \n${violations.joinToString("\n")}\n${if (id == null) "" else "id: $id"}")
    }
  }

  // This method cannot be inlined because of magic Kotlin compilation bug: it 'captured' "list" local value and cause class-loader leak
  // See IDEA-CR-61904
  internal fun registerEpListeners() {
    ToolWindowEP.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<ToolWindowEP> {
      override fun extensionAdded(extension: ToolWindowEP, pluginDescriptor: PluginDescriptor) {
        coroutineScope.launch {
          initToolWindow(extension, pluginDescriptor)
        }
      }

      override fun extensionRemoved(extension: ToolWindowEP, pluginDescriptor: PluginDescriptor) {
        doUnregisterToolWindow(extension.id)
      }
    }, project)
  }

  internal fun log(): Logger = LOG
}

private enum class KeyState {
  WAITING, PRESSED, RELEASED, HOLD
}

private fun areAllModifiersPressed(
  @MagicConstant(flagsFromClass = InputEvent::class) modifiers: Int,
  @MagicConstant(flagsFromClass = InputEvent::class) mask: Int,
): Boolean {
  return (modifiers xor mask) == 0
}

@MagicConstant(flagsFromClass = InputEvent::class)
@Suppress("DEPRECATION")
private fun keyCodeToInputMask(code: Int): Int {
  return when (code) {
    KeyEvent.VK_SHIFT -> InputEvent.SHIFT_MASK
    KeyEvent.VK_CONTROL -> InputEvent.CTRL_MASK
    KeyEvent.VK_META -> InputEvent.META_MASK
    KeyEvent.VK_ALT -> InputEvent.ALT_MASK
    else -> 0
  }
}

// We should filter out 'mixed' mask like InputEvent.META_MASK | InputEvent.META_DOWN_MASK
@MagicConstant(flagsFromClass = InputEvent::class)
private fun getActivateToolWindowVKsMask(): Int {
  if (!LoadingState.COMPONENTS_LOADED.isOccurred) {
    return 0
  }

  if (Registry.`is`("toolwindow.disable.overlay.by.double.key")) {
    return 0
  }

  val baseShortcut = KeymapManager.getInstance().activeKeymap.getShortcuts("ActivateProjectToolWindow")

  @Suppress("DEPRECATION")
  val defaultModifiers = if (SystemInfoRt.isMac) InputEvent.META_MASK else InputEvent.ALT_MASK
  var baseModifiers = 0
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
  @Suppress("DEPRECATION")
  baseModifiers = baseModifiers and (InputEvent.SHIFT_MASK or InputEvent.CTRL_MASK or InputEvent.META_MASK or InputEvent.ALT_MASK)

  // If the keymap either doesn't define an ActivateProjectToolWindow shortcut
  // or defines it with multiple modifiers, fall back to the default behavior.
  if (baseModifiers.countOneBits() == 1) {
    return baseModifiers
  } else {
    return defaultModifiers
  }
}

private val isStackEnabled: Boolean
  get() = Registry.`is`("ide.enable.toolwindow.stack")

private fun getToolWindowIdForComponent(component: Component?): String? {
  var c = component
  while (c != null) {
    if (c is InternalDecoratorImpl) {
      return c.toolWindow.id
    }
    c = ClientProperty.get(c, ToolWindowManagerImpl.PARENT_COMPONENT) ?: c.parent
  }
  return null
}

private class BalloonHyperlinkListener(private val listener: HyperlinkListener?) : HyperlinkListener {
  var balloon: Balloon? = null

  override fun hyperlinkUpdate(e: HyperlinkEvent) {
    val balloon = balloon
    if (balloon != null && e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
      SwingUtilities.invokeLater { balloon.hide() }
    }
    listener?.hyperlinkUpdate(e)
  }
}

private fun windowInfoChanges(oldInfo: WindowInfo, newInfo: WindowInfo): String {
  if (oldInfo !is WindowInfoImpl || newInfo !is WindowInfoImpl) {
    return "Logging of non-standard WindowInfo implementations is not supported"
  }
  val sb = StringBuilder("Changes:")
  for ((index, newProperty) in newInfo.__getProperties().withIndex()) {
    val oldProperty = oldInfo.__getProperties().getOrNull(index)
    val name = newProperty.name
    if (oldProperty == null || oldProperty.name != name) {
      return "Old and new window info don't have the same property set: old=$oldInfo, new=$newInfo"
    }
    if (newProperty != oldProperty) {
      sb.append(' ').append(oldProperty).append(" -> ").append(newProperty)
    }
  }
  return sb.toString()
}

private fun isToolwindowOfBundledPlugin(task: RegisterToolWindowTask): Boolean {
  // platform toolwindow but registered dynamically
  if (ToolWindowId.BUILD_DEPENDENCIES == task.id) {
    return true
  }

  task.pluginDescriptor?.let {
    return it.isBundled
  }

  // check content factory, Service View, and Endpoints View goes here
  val pluginDescriptor = PluginManager.getPluginByClass(task.contentFactory?.javaClass ?: return false)
  return pluginDescriptor == null || pluginDescriptor.isBundled
}
