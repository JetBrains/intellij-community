// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "OverridingDeprecatedMember", "ReplaceNegatedIsEmptyWithIsNotEmpty",
               "PrivatePropertyName")

package com.intellij.openapi.wm.impl

import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.PluginException
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.UiActivity
import com.intellij.ide.UiActivityMonitor
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.actions.MaximizeActiveDialogAction
import com.intellij.ide.plugins.PluginManagerCore
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
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.*
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.ui.FrameWrapper
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
import com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType
import com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType.MovedOrResized
import com.intellij.serviceContainer.NonInjectable
import com.intellij.toolWindow.*
import com.intellij.ui.BalloonImpl
import com.intellij.ui.ClientProperty
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.*
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.*
import kotlinx.coroutines.*
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeListener
import java.lang.Runnable
import java.util.*
import java.util.concurrent.TimeUnit
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

private val LOG = logger<ToolWindowManagerImpl>()

private typealias Mutation = ((WindowInfoImpl) -> Unit)

@ApiStatus.Internal
open class ToolWindowManagerImpl @NonInjectable @TestOnly internal constructor(
  val project: Project,
  @field:JvmField internal val isNewUi: Boolean,
  private val isEdtRequired: Boolean,
) : ToolWindowManagerEx(), Disposable {
  private val dispatcher = EventDispatcher.create(ToolWindowManagerListener::class.java)

  private val stripeManager = ToolWindowStripeManager.getInstance(project)

  private val state: ToolWindowManagerState by lazy(LazyThreadSafetyMode.NONE) { project.service() }

  var layoutState
    get() = state.layout
    set(value) { state.layout = value }

  private val idToEntry = HashMap<String, ToolWindowEntry>()
  private val activeStack = ActiveStack()
  private val sideStack = SideStack()
  private val toolWindowPanes = LinkedHashMap<String, ToolWindowPane>()

  private var frameHelper: ProjectFrameHelper?
    get() = state.frame
    set(value) { state.frame = value }

  override var layoutToRestoreLater: DesktopLayout?
    get() = state.layoutToRestoreLater
    set(value) { state.layoutToRestoreLater = value }
  private var currentState = KeyState.WAITING
  private val waiterForSecondPress: SingleAlarm?
  private val recentToolWindowsState: LinkedList<String>
    get() = state.recentToolWindows

  @Suppress("LeakingThis")
  private val toolWindowSetInitializer = ToolWindowSetInitializer(project, this)

  @Suppress("TestOnlyProblems")
  constructor(project: Project) : this(project, isNewUi = ExperimentalUI.isNewUI(), isEdtRequired = true)

  init {
    if (project.isDefault) {
      waiterForSecondPress = null
    }
    else {
      waiterForSecondPress = SingleAlarm(
        task = {
          if (currentState != KeyState.HOLD) {
            resetHoldState()
          }
        },
        delay = SystemProperties.getIntProperty("actionSystem.keyGestureDblClickTime", 650),
        parentDisposable = (project as ProjectEx).earlyDisposable
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

  fun isToolWindowRegistered(id: String) = idToEntry.containsKey(id)

  internal fun getEntry(id: String) = idToEntry.get(id)

  internal fun assertIsEdt() {
    if (isEdtRequired) {
      EDT.assertIsEdt()
    }
  }

  override fun dispose() {
  }

  @Service(Service.Level.APP)
  internal class ToolWindowManagerAppLevelHelper {
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
          processOpenedProjects { project ->
            for (composite in (FileEditorManagerEx.getInstanceExIfCreated(project) ?: return).activeSplittersComposites) {
              if (composite.allEditors.any { SwingUtilities.isDescendingFrom(component, it.component) }) {
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

      val updateHeadersAlarm = SingleAlarm({
        processOpenedProjects { project ->
          (getInstance(project) as ToolWindowManagerImpl).updateToolWindowHeaders()
        }
      }, 50, ApplicationManager.getApplication())
      val focusListener = PropertyChangeListener { updateHeadersAlarm.cancelAndRequest() }
      FocusUtil.addFocusOwnerListener(ApplicationManager.getApplication(), focusListener)

      val connection = ApplicationManager.getApplication().messageBus.connect()
      connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
        override fun projectClosingBeforeSave(project: Project) {
          val manager = (project.serviceIfCreated<ToolWindowManager>() as ToolWindowManagerImpl?) ?: return
          for (entry in manager.idToEntry.values) {
            manager.saveFloatingOrWindowedState(entry, manager.layoutState.getInfo(entry.id) ?: continue)
          }
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
      }, ApplicationManager.getApplication())
    }
  }

  private fun getDefaultToolWindowPane() = toolWindowPanes.get(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)!!

  internal fun getToolWindowPane(paneId: String) = toolWindowPanes.get(paneId) ?: getDefaultToolWindowPane()

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
    ApplicationManager.getApplication().invokeLater({ buttonManagers.forEach { it.refreshUi() } }, project.disposed)
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
    focusManager.doWhenFocusSettlesDown(ExpirableRunnable.forProject(project) {
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

    val parent = e.component?.let { ComponentUtil.findUltimateParent(it) }
    if (parent is IdeFrame) {
      if ((parent as IdeFrame).project !== project) {
        resetHoldState()
        return false
      }
    }

    val vks = getActivateToolWindowVKsMask()
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

  suspend fun init(frameHelper: ProjectFrameHelper, reopeningEditorsJob: Job, taskListDeferred: Deferred<List<RegisterToolWindowTask>>) {
    doInit(frameHelper = frameHelper,
           connection = project.messageBus.connect(this),
           reopeningEditorsJob = reopeningEditorsJob,
           taskListDeferred = taskListDeferred)
  }

  @VisibleForTesting
  suspend fun doInit(frameHelper: ProjectFrameHelper,
                     connection: MessageBusConnection,
                     reopeningEditorsJob: Job,
                     taskListDeferred: Deferred<List<RegisterToolWindowTask>>?) {
    connection.subscribe(ToolWindowManagerListener.TOPIC, dispatcher.multicaster)
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      this@ToolWindowManagerImpl.frameHelper = frameHelper

      // Make sure we haven't already created the root tool window pane. We might have created panes for secondary frames, as they get
      // registered differently, but we shouldn't have the main pane yet
      LOG.assertTrue(!toolWindowPanes.containsKey(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID))

      val toolWindowPane = frameHelper.rootPane.getToolWindowPane()
      // This will be the tool window pane for the default frame, which is not automatically added by the ToolWindowPane constructor. If we're
      // reopening other frames, their tool window panes will be already added, but we still need to initialise the tool windows themselves.
      toolWindowPanes.put(toolWindowPane.paneId, toolWindowPane)
    }

    toolWindowSetInitializer.initUi(reopeningEditorsJob, taskListDeferred)

    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        @Suppress("DEPRECATION")
        project.coroutineScope.launch(Dispatchers.EDT) {
          focusManager.doWhenFocusSettlesDown(ExpirableRunnable.forProject(project) {
            if (!FileEditorManager.getInstance(project).hasOpenFiles()) {
              focusToolWindowByDefault()
            }
          })
        }.cancelOnDispose(this@ToolWindowManagerImpl)
      }
    })
  }

  @Deprecated("Use {{@link #registerToolWindow(RegisterToolWindowTask)}}")
  override fun initToolWindow(bean: ToolWindowEP) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    initToolWindow(bean, bean.pluginDescriptor)
  }

  internal fun initToolWindow(bean: ToolWindowEP, plugin: PluginDescriptor) {
    val condition = bean.getCondition(plugin)
    if (condition != null && !condition.value(project)) {
      return
    }

    val factory = bean.getToolWindowFactory(bean.pluginDescriptor)
    if (!factory.isApplicable(project)) {
      return
    }

    // Always add to the default tool window pane
    val toolWindowPane = getDefaultToolWindowPaneIfInitialized()
    val anchor = getToolWindowAnchor(factory, bean)

    @Suppress("DEPRECATION")
    val sideTool = (bean.secondary || bean.side) && !isNewUi
    val entry = registerToolWindow(RegisterToolWindowTask(
      id = bean.id,
      icon = findIconFromBean(bean, factory, plugin),
      anchor = anchor,
      sideTool = sideTool,
      canCloseContent = bean.canCloseContents,
      canWorkInDumbMode = DumbService.isDumbAware(factory),
      shouldBeAvailable = factory.shouldBeAvailable(project),
      contentFactory = factory,
      stripeTitle = getStripeTitleSupplier(bean.id, project, plugin)
    ).apply {
      pluginDescriptor = plugin
    }, toolWindowPane.buttonManager)
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowsRegistered(listOf(entry.id), this)

    toolWindowPane.buttonManager.getStripeFor(anchor, sideTool).revalidate()
    toolWindowPane.validate()
    toolWindowPane.repaint()
  }

  private fun getDefaultToolWindowPaneIfInitialized(): ToolWindowPane {
    return toolWindowPanes.get(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)
           ?: throw IllegalStateException("You must not register toolwindow programmatically so early. " +
                                          "Rework code or use ToolWindowManager.invokeLater")
  }

  private fun loadDefault() {
    toolWindowSetInitializer.scheduleSetLayout(ToolWindowDefaultLayoutManager.getInstance().getLayoutCopy())
  }

  @Deprecated("Use {@link ToolWindowManagerListener#TOPIC}", level = DeprecationLevel.ERROR)
  override fun addToolWindowManagerListener(listener: ToolWindowManagerListener) {
    dispatcher.addListener(listener)
  }

  @Deprecated("Use {@link ToolWindowManagerListener#TOPIC}", level = DeprecationLevel.ERROR,
              replaceWith = ReplaceWith("project.messageBus.connect(parentDisposable).subscribe(ToolWindowManagerListener.TOPIC, listener)",
                                                    "com.intellij.openapi.wm.ex.ToolWindowManagerListener"))
  override fun addToolWindowManagerListener(listener: ToolWindowManagerListener, parentDisposable: Disposable) {
    project.messageBus.connect(parentDisposable).subscribe(ToolWindowManagerListener.TOPIC, listener)
  }

  @Deprecated("Use {@link ToolWindowManagerListener#TOPIC}", level = DeprecationLevel.ERROR)
  override fun removeToolWindowManagerListener(listener: ToolWindowManagerListener) {
    dispatcher.removeListener(listener)
  }

  override fun activateEditorComponent() {
    EditorsSplitters.focusDefaultComponentInSplittersIfPresent(project)
  }

  open fun activateToolWindow(id: String, runnable: Runnable?, autoFocusContents: Boolean, source: ToolWindowEventSource? = null) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    val activity = UiActivity.Focus("toolWindow:$id")
    UiActivityMonitor.getInstance().addActivity(project, activity, ModalityState.NON_MODAL)

    activateToolWindow(idToEntry.get(id)!!, getRegisteredMutableInfoOrLogError(id), autoFocusContents, source)

    ApplicationManager.getApplication().invokeLater({
      runnable?.run()
      UiActivityMonitor.getInstance().removeActivity(project, activity)
    }, project.disposed)
  }

  internal fun activateToolWindow(entry: ToolWindowEntry,
                                  info: WindowInfoImpl,
                                  autoFocusContents: Boolean = true,
                                  source: ToolWindowEventSource? = null) {
    LOG.debug { "activateToolWindow($entry)" }

    if (!isIndependentToolWindowResizeEnabled()) {
      val visibleToolWindow = visibleToolWindow(info.anchor)
      if (visibleToolWindow != null) {
        info.weight = visibleToolWindow.readOnlyWindowInfo.weight
      }
    }

    if (source != null) {
      ToolWindowCollector.getInstance().recordActivation(project, entry.id, info, source)
    }

    recentToolWindowsState.remove(entry.id)
    recentToolWindowsState.add(0, entry.id)

    if (!entry.toolWindow.isAvailable) {
      // Tool window can be "logically" active but not focused.
      // For example, when the user switched to another application. So we just need to bring tool window's window to front.
      if (autoFocusContents && !entry.toolWindow.hasFocus) {
        entry.toolWindow.requestFocusInToolWindow()
      }

      return
    }

    if (!entry.readOnlyWindowInfo.isVisible) {
      info.isActiveOnStart = autoFocusContents
      showToolWindowImpl(entry, info, dirtyMode = false, source = source)
    }

    if (autoFocusContents && ApplicationManager.getApplication().isActive) {
      entry.toolWindow.requestFocusInToolWindow()
    }
    else {
      activeStack.push(entry)
    }

    fireStateChanged(ToolWindowManagerEventType.ActivateToolWindow)
  }

  private fun isIndependentToolWindowResizeEnabled(): Boolean =
    if (isNewUi)
      UISettings.getInstance().rememberSizeForEachToolWindow
    else
      Registry.`is`("toolwindow.independent.sizes")

  private fun visibleToolWindow(anchor: ToolWindowAnchor): ToolWindowEntry? =
    idToEntry.values.firstOrNull { it.isVisibleAndDockedTo(anchor) }

  private fun ToolWindowEntry.isVisibleAndDockedTo(anchor: ToolWindowAnchor) =
    toolWindow.isVisible && readOnlyWindowInfo.isDocked && readOnlyWindowInfo.anchor == anchor

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

  private fun deactivateToolWindow(info: WindowInfoImpl,
                                   entry: ToolWindowEntry,
                                   dirtyMode: Boolean = false,
                                   mutation: Mutation? = null,
                                   source: ToolWindowEventSource? = null) {
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
      val frame = toolWindowPanes.values.firstOrNull { it.frame.isActive }?.frame ?: frameHelper?.frame ?: return null
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

  /**
   * @return windowed decorator for the tool window with specified `ID`.
   */
  private fun getWindowedDecorator(id: String) = idToEntry.get(id)?.windowedDecorator

  override fun getIdsOn(anchor: ToolWindowAnchor) = getVisibleToolWindowsOn(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID, anchor).map { it.id }.toList()

  internal fun getToolWindowsOn(paneId: String, anchor: ToolWindowAnchor, excludedId: String): MutableList<ToolWindowEx> {
    return getVisibleToolWindowsOn(paneId, anchor)
      .filter { it.id != excludedId }
      .map { it.toolWindow }
      .toMutableList()
  }

  internal fun getDockedInfoAt(paneId: String, anchor: ToolWindowAnchor?, side: Boolean): WindowInfo? {
    return idToEntry.values.asSequence()
      .map { it.readOnlyWindowInfo }
      .find { it.isVisible && it.isDocked && it.safeToolWindowPaneId == paneId &&  it.anchor == anchor && it.isSplit == side }
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
      fireStateChanged(ToolWindowManagerEventType.ShowToolWindow)
    }
  }

  override fun hideToolWindow(id: String, hideSide: Boolean) {
    hideToolWindow(id = id, hideSide = hideSide, source = null)
  }

  open fun hideToolWindow(id: String,
                          hideSide: Boolean = false,
                          moveFocus: Boolean = true,
                          removeFromStripe: Boolean = false,
                          source: ToolWindowEventSource? = null) {
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
    fireStateChanged(ToolWindowManagerEventType.HideToolWindow)
    if (moveFocusAfter) {
      activateEditorComponent()
    }
    revalidateStripeButtons()
  }

  private fun executeHide(entry: ToolWindowEntry,
                          info: WindowInfoImpl,
                          dirtyMode: Boolean,
                          hideSide: Boolean = false,
                          mutation: Mutation? = null,
                          source: ToolWindowEventSource? = null) {
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
      // first we have to find tool window that was located at the same side and was hidden
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
  private fun showToolWindowImpl(entry: ToolWindowEntry,
                                 toBeShownInfo: WindowInfoImpl,
                                 dirtyMode: Boolean,
                                 source: ToolWindowEventSource? = null): Boolean {
    if (!entry.toolWindow.isAvailable) {
      return false
    }

    ToolWindowCollector.getInstance().recordShown(project, source, toBeShownInfo)
    toBeShownInfo.isVisible = true
    toBeShownInfo.isShowStripeButton = true

    val snapshotInfo = toBeShownInfo.copy()
    entry.applyWindowInfo(snapshotInfo)
    doShowWindow(entry, snapshotInfo, dirtyMode)

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
      // is docked and not auto-hide one). Therefore, it's possible to restore the
      // hidden tool window when showing tool window will be closed.
      for (otherEntry in idToEntry.values) {
        if (entry.id == otherEntry.id) {
          continue
        }

        val otherInfo = otherEntry.readOnlyWindowInfo
        if (otherInfo.isVisible && otherInfo.type == info.type && otherInfo.isSplit == info.isSplit
            && otherInfo.safeToolWindowPaneId == info.safeToolWindowPaneId &&  otherInfo.anchor == info.anchor) {
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

      // remove tool window from the SideStack
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
    ApplicationManager.getApplication().assertIsDispatchThread()

    // Try to get a previously saved tool window pane, if possible
    val toolWindowPane = this.getLayout().getInfo(task.id)?.toolWindowPaneId?.let { getToolWindowPane(it) }
                         ?: getDefaultToolWindowPaneIfInitialized()
    val entry = registerToolWindow(task, buttonManager = toolWindowPane.buttonManager)
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowsRegistered(listOf(entry.id), this)

    toolWindowPane.buttonManager.getStripeFor(entry.toolWindow.anchor, entry.toolWindow.isSplitMode).revalidate()

    toolWindowPane.validate()
    toolWindowPane.repaint()
    fireStateChanged(ToolWindowManagerEventType.RegisterToolWindow)
    return entry.toolWindow
  }

  internal fun registerToolWindow(task: RegisterToolWindowTask,
                                  buttonManager: ToolWindowButtonManager,
                                  ensureToolWindowActionRegisteredIsNeeded: Boolean = true): ToolWindowEntry {
    LOG.debug { "registerToolWindow($task)" }

    if (idToEntry.containsKey(task.id)) {
      throw IllegalArgumentException("window with id=\"${task.id}\" is already registered")
    }

    var info = layoutState.getInfo(task.id)
    val isButtonNeeded = task.shouldBeAvailable
                         && (info?.isShowStripeButton ?: !(isNewUi && isToolwindowOfBundledPlugin(task)))
                         && stripeManager.allowToShowOnStripe(task.id, info == null, isNewUi)
    // do not create layout for New UI - button is not created for toolwindow by default
    if (info == null) {
      info = layoutState.create(task, isNewUi = isNewUi)
      if (isButtonNeeded) {
        // we must allocate order - otherwise, on drag-n-drop, we cannot move some tool windows to the end
        // because sibling's order is equal to -1, so, always in the end
        info.order = layoutState.getMaxOrder(info.safeToolWindowPaneId, task.anchor)
        layoutState.addInfo(task.id, info)
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

    val stripeTitle = task.stripeTitle?.get() ?: task.id
    val toolWindow = ToolWindowImpl(toolWindowManager = this,
                                    id = task.id,
                                    canCloseContent = task.canCloseContent,
                                    dumbAware = task.canWorkInDumbMode,
                                    component = task.component,
                                    parentDisposable = disposable,
                                    windowInfo = infoSnapshot,
                                    contentFactory = factory,
                                    isAvailable = task.shouldBeAvailable,
                                    stripeTitle = stripeTitle)
    if (task.hideOnEmptyContent) {
      toolWindow.setToHideOnEmptyContent(true)
    }
    toolWindow.windowInfoDuringInit = infoSnapshot
    try {
      factory?.init(toolWindow)
    }
    catch (e: IllegalStateException) {
      LOG.error(PluginException(e, task.pluginDescriptor?.pluginId))
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

    if (ensureToolWindowActionRegisteredIsNeeded) {
      ActivateToolWindowAction.ensureToolWindowActionRegistered(toolWindow, ActionManager.getInstance())
    }

    val stripeButton = if (isButtonNeeded) {
      buttonManager.createStripeButton(toolWindow, infoSnapshot, task)
    }
    else {
      LOG.debug {
        "Button is not created for `${task.id}`" +
        "(isShowStripeButton: ${info.isShowStripeButton}, isAvailable: ${task.shouldBeAvailable})"
      }
      null
    }

    val entry = ToolWindowEntry(stripeButton, toolWindow, disposable)
    idToEntry.put(task.id, entry)

    // If preloaded info is visible or active then we have to show/activate the installed
    // tool window. This step has sense only for windows which are not in the auto hide
    // mode. But if tool window was active but its mode doesn't allow to activate it again
    // (for example, tool window is in auto hide mode) then we just activate editor component.
    if (stripeButton != null && factory != null /* not null on init tool window from EP */ && infoSnapshot.isVisible) {
      showToolWindowImpl(entry, info, dirtyMode = false)

      // do not activate tool window that is the part of project frame - default component should be focused
      if (infoSnapshot.isActiveOnStart &&
          (infoSnapshot.type == ToolWindowType.WINDOWED || infoSnapshot.type == ToolWindowType.FLOATING) &&
          ApplicationManager.getApplication().isActive) {
        entry.toolWindow.requestFocusInToolWindow()
      }
    }

    return entry
  }

  private fun isToolwindowOfBundledPlugin(task: RegisterToolWindowTask): Boolean {
    val taskPlugin = task.pluginDescriptor
    if (taskPlugin != null) return taskPlugin.isBundled

    val contentFactoryClass = task.contentFactory?.javaClass?.canonicalName ?: return false
    // check content factory, Service View and Endpoints View go here
    val pluginDescriptor = PluginManagerCore.getPluginDescriptorOrPlatformByClassName(contentFactoryClass)
    return pluginDescriptor == null || pluginDescriptor.isBundled
  }

  @Deprecated("Use ToolWindowFactory and toolWindow extension point")
  @Suppress("OverridingDeprecatedMember")
  override fun unregisterToolWindow(id: String) {
    doUnregisterToolWindow(id)
    fireStateChanged(ToolWindowManagerEventType.UnregisterToolWindow)
  }

  internal fun doUnregisterToolWindow(id: String) {
    LOG.debug { "unregisterToolWindow($id)" }

    ApplicationManager.getApplication().assertIsDispatchThread()
    ActivateToolWindowAction.unregister(id)

    val entry = idToEntry.remove(id) ?: return
    val toolWindow = entry.toolWindow

    val info = layoutState.getInfo(id)
    if (info != null) {
      // remove decorator and tool button from the screen - removing will also save current bounds
      updateStateAndRemoveDecorator(info, entry, false)
      // save recent appearance of tool window
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
      val toolWindowPane = getToolWindowPane(state.safeToolWindowPaneId)
      toolWindowPane.removeDecorator(state, it, dirtyMode, this)
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
    return layoutState
  }

  @VisibleForTesting
  fun setLayoutOnInit(newLayout: DesktopLayout) {
    if (!idToEntry.isEmpty()) {
      LOG.error("idToEntry must be empty (idToEntry={\n${idToEntry.entries.joinToString(separator = "\n") { (k, v) -> "$k: $v" }})")
    }
    layoutState = newLayout
  }

  override fun setLayout(newLayout: DesktopLayout) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    if (idToEntry.isEmpty()) {
      layoutState = newLayout
      return
    }

    data class LayoutData(val old: WindowInfoImpl, val new: WindowInfoImpl, val entry: ToolWindowEntry)

    val list = mutableListOf<LayoutData>()

    for (entry in idToEntry.values) {
      val old = layoutState.getInfo(entry.id) ?: entry.readOnlyWindowInfo as WindowInfoImpl
      val new = newLayout.getInfo(entry.id)
      // just copy if defined in the old layout but not in the new
      if (new == null) {
        newLayout.addInfo(entry.id, old.copy())
      }
      else if (old != new) {
        list.add(LayoutData(old = old, new = new, entry = entry))
      }
    }

    this.layoutState = newLayout

    if (list.isEmpty()) {
      return
    }

    for (item in list) {
      item.entry.applyWindowInfo(item.new)

      if (item.old.isVisible && !item.new.isVisible) {
        updateStateAndRemoveDecorator(item.new, item.entry, dirtyMode = true)
      }

      if (item.old.safeToolWindowPaneId != item.new.safeToolWindowPaneId
          || item.old.anchor != item.new.anchor
          || item.old.order != item.new.order) {
        setToolWindowAnchorImpl(item.entry, item.old, item.new, item.new.safeToolWindowPaneId, item.new.anchor, item.new.order, null)
      }

      var toShowWindow = false

      if (item.old.isSplit != item.new.isSplit) {
        val wasVisible = item.old.isVisible
        // we should hide the window and show it in a 'new place' to automatically hide possible window that is already located in a 'new place'
        if (wasVisible) {
          hideToolWindow(item.entry.id)
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
      } else if (item.new.isVisible && item.old.isDocked && item.new.isDocked && item.old.weight != item.new.weight) {
        getToolWindowPane(item.entry.toolWindow).setWeight(item.entry.toolWindow, item.new.weight)
      }

      if (toShowWindow) {
        doShowWindow(item.entry, item.new, dirtyMode = true)
      }
    }

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

  override fun invokeLater(runnable: Runnable) {
    if (!toolWindowSetInitializer.addToPendingTasksIfNotInitialized(runnable)) {
      ApplicationManager.getApplication().invokeLater(runnable, ModalityState.NON_MODAL, project.disposed)
    }
  }

  override val focusManager: IdeFocusManager
    get() = IdeFocusManager.getInstance(project)!!

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
    when(anchor) {
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
    val existing = entry.balloon
    if (existing != null) {
      Disposer.dispose(existing)
    }

    val anchor = entry.readOnlyWindowInfo.anchor
    val position = Ref(Balloon.Position.atLeft)
    when (anchor) {
      ToolWindowAnchor.TOP -> position.set(Balloon.Position.atRight)
      ToolWindowAnchor.RIGHT -> position.set(Balloon.Position.atRight)
      ToolWindowAnchor.BOTTOM -> position.set(Balloon.Position.atLeft)
      ToolWindowAnchor.LEFT -> position.set(Balloon.Position.atLeft)
    }

    val balloon = createBalloon(options, entry)
    val toolWindowPane = getToolWindowPane(entry.readOnlyWindowInfo.safeToolWindowPaneId)
    val buttonManager = toolWindowPane.buttonManager as ToolWindowPaneNewButtonManager
    var button = buttonManager.getSquareStripeFor(entry.readOnlyWindowInfo.anchor).getButtonFor(options.toolWindowId)?.getComponent()
    if (button == null || !button.isShowing) {
      button = (buttonManager.getSquareStripeFor(ToolWindowAnchor.LEFT) as? ToolWindowLeftToolbar)?.moreButton!!
      position.set(Balloon.Position.atLeft)
    }
    val show = Runnable {
      val tracker: PositionTracker<Balloon>
      if (entry.toolWindow.isVisible &&
          (entry.toolWindow.type == ToolWindowType.WINDOWED ||
           entry.toolWindow.type == ToolWindowType.FLOATING)) {
        tracker = createPositionTracker(entry.toolWindow.component, ToolWindowAnchor.BOTTOM)
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
                                 Point(if (position.get() == Balloon.Position.atRight) 0 else button.bounds.width, button.height / 2))
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

  private fun createPositionTracker(component: Component, anchor: ToolWindowAnchor): PositionTracker<Balloon> {
    return object : PositionTracker<Balloon>(component) {
      override fun recalculateLocation(balloon: Balloon): RelativePoint {
        val bounds = component.bounds
        val target = StartupUiUtil.getCenterPoint(bounds, Dimension(1, 1))
        when(anchor) {
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

    val content = options.htmlBody.replace("\n", "<br>")
    val balloonBuilder = JBPopupFactory.getInstance()
      .createHtmlTextBalloonBuilder(content, options.icon, options.type.titleForeground, options.type.popupBackground, listenerWrapper)
      .setBorderColor(options.type.borderColor)
      .setHideOnClickOutside(false)
      .setHideOnFrameResize(false)

    options.balloonCustomizer?.accept(balloonBuilder)

    val balloon = balloonBuilder.createBalloon()
    if (balloon is BalloonImpl) {
      NotificationsManagerImpl.frameActivateBalloonListener(balloon) {
        EdtExecutorService.getScheduledExecutorInstance().schedule({ balloon.setHideOnClickOutside(true) }, 100, TimeUnit.MILLISECONDS)
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

  override fun getToolWindowBalloon(id: String) = idToEntry[id]?.balloon

  override val isEditorComponentActive: Boolean get() = state.isEditorComponentActive

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
    setToolWindowAnchorImpl(entry, info, getRegisteredMutableInfoOrLogError(id), info.safeToolWindowPaneId, anchor, order, layoutState)
    getToolWindowPane(info.safeToolWindowPaneId).validateAndRepaint()
    fireStateChanged(ToolWindowManagerEventType.SetToolWindowAnchor)
  }

  fun setVisibleOnLargeStripe(id: String, visible: Boolean) {
    val info = getRegisteredMutableInfoOrLogError(id)
    info.isShowStripeButton = visible
    idToEntry.get(info.id)!!.applyWindowInfo(info.copy())
    fireStateChanged(ToolWindowManagerEventType.SetVisibleOnLargeStripe)
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

    // if tool window isn't visible or only order number is changed then just remove/add stripe button
    if (!currentInfo.isVisible || (paneId == currentInfo.safeToolWindowPaneId && anchor == currentInfo.anchor) ||
        currentInfo.type == ToolWindowType.FLOATING || currentInfo.type == ToolWindowType.WINDOWED) {
      doSetAnchor(entry, layoutInfo, paneId, anchor, order, currentInfo, layoutState)
    }
    else {
      val wasFocused = entry.toolWindow.isActive
      // for docked and sliding windows we have to move buttons and window's decorators
      layoutInfo.isVisible = false
      toolWindowPane.removeDecorator(currentInfo, entry.toolWindow.decoratorComponent, /* dirtyMode = */ true, this)

      doSetAnchor(entry, layoutInfo, paneId, anchor, order, currentInfo, layoutState)

      showToolWindowImpl(entry, layoutInfo, false)
      if (wasFocused) {
        entry.toolWindow.requestFocusInToolWindow()
      }
    }
  }

  private fun doSetAnchor(entry: ToolWindowEntry,
                          info: WindowInfoImpl,
                          paneId: String,
                          anchor: ToolWindowAnchor,
                          order: Int,
                          currentInfo: WindowInfo? = null,
                          layoutState: DesktopLayout?) {
    if (isNewUi && currentInfo != null) {
      entry.removeStripeButton(currentInfo.anchor, currentInfo.isSplit)
    } else {
      entry.removeStripeButton()
    }

    if (layoutState != null) {
      for (otherInfo in layoutState.setAnchor(info, paneId, anchor, order)) {
        idToEntry.get(otherInfo.id ?: continue)?.toolWindow?.setWindowInfoSilently(otherInfo.copy())
      }
    }

    entry.toolWindow.applyWindowInfo(info.copy())
    entry.stripeButton = getToolWindowPane(paneId).buttonManager.createStripeButton(entry.toolWindow, info, task = null)
  }

  internal fun setSideTool(id: String, isSplit: Boolean) {
    val entry = idToEntry.get(id)
    if (entry == null) {
      LOG.error("Cannot set side tool: toolwindow $id is not registered")
      return
    }

    if (entry.readOnlyWindowInfo.isSplit != isSplit) {
      setSideTool(entry, getRegisteredMutableInfoOrLogError(id), isSplit)
      fireStateChanged(ToolWindowManagerEventType.SetSideTool)
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
        otherEntry.applyWindowInfo((layoutState.getInfo(otherEntry.id) ?: continue).copy())
      }
    }
    getToolWindowPane(info.safeToolWindowPaneId).buttonManager.getStripeFor(entry.readOnlyWindowInfo.anchor,
                                                                            entry.readOnlyWindowInfo.isSplit).revalidate()
  }

  fun setContentUiType(id: String, type: ToolWindowContentUiType) {
    val info = getRegisteredMutableInfoOrLogError(id)
    info.contentUiType = type
    idToEntry.get(info.id!!)!!.applyWindowInfo(info.copy())
    fireStateChanged(ToolWindowManagerEventType.SetContentUiType)
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
    fireStateChanged(ToolWindowManagerEventType.SetSideToolAndAnchor)
  }

  private fun hideIfNeededAndShowAfterTask(entry: ToolWindowEntry,
                                           info: WindowInfoImpl,
                                           source: ToolWindowEventSource? = null,
                                           task: () -> Unit) {
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

  protected open fun fireStateChanged(changeType: ToolWindowManagerEventType) {
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).stateChanged(this, changeType)
  }

  private fun fireToolWindowShown(toolWindow: ToolWindow) {
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowShown(toolWindow)
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

    fireStateChanged(ToolWindowManagerEventType.SetToolWindowAutoHide)
  }

  fun setToolWindowType(id: String, type: ToolWindowType) {
    ApplicationManager.getApplication().assertIsDispatchThread()

    val entry = idToEntry.get(id)!!
    if (entry.readOnlyWindowInfo.type == type) {
      return
    }

    setToolWindowTypeImpl(entry, getRegisteredMutableInfoOrLogError(entry.id), type)
    fireStateChanged(ToolWindowManagerEventType.SetToolWindowType)
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
    if (type != ToolWindowType.FLOATING && type != ToolWindowType.WINDOWED) {
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

  override fun isMaximized(window: ToolWindow) = getToolWindowPane(window).isMaximized(window)

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
    val bounds = info.floatingBounds
    if ((bounds != null && bounds.width > 0 && bounds.height > 0 &&
         WindowManager.getInstance().isInsideScreenBounds(bounds.x, bounds.y, bounds.width))) {
      floatingDecorator.bounds = Rectangle(bounds)
    }
    else {
      // place new frame at the center of current frame if there are no floating bounds
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
    val app = ApplicationManager.getApplication()
    if (app.isHeadlessEnvironment || app.isUnitTestMode) {
      return
    }

    val id = entry.id
    val decorator = entry.toolWindow.getOrCreateDecoratorComponent()
    val windowedDecorator = FrameWrapper(project, title = "${entry.toolWindow.stripeTitle} - ${project.name}", component = decorator)
    val window = windowedDecorator.getFrame()

    MnemonicHelper.init((window as RootPaneContainer).contentPane)

    val shouldBeMaximized = info.isMaximized
    val bounds = info.floatingBounds
    if ((bounds != null && bounds.width > 0 && (bounds.height > 0) &&
         WindowManager.getInstance().isInsideScreenBounds(bounds.x, bounds.y, bounds.width))) {
      window.bounds = Rectangle(bounds)
    }
    else {
      // place new frame at the center of current frame if there are no floating bounds
      val currentFrame = getToolWindowPane(entry.toolWindow).frame
      var size = decorator.size
      if (size.width == 0 || size.height == 0) {
        size = decorator.preferredSize
      }
      window.size = size
      window.setLocationRelativeTo(currentFrame)
    }
    entry.windowedDecorator = windowedDecorator
    Disposer.register(windowedDecorator) {
      if (idToEntry.get(id)?.windowedDecorator != null) {
        hideToolWindow(id, false)
      }
    }

    window.isAutoRequestFocus = info.isActiveOnStart
    try {
      windowedDecorator.show(false)
    } finally {
      window.isAutoRequestFocus = true
    }

    val rootPane = (window as RootPaneContainer).rootPane
    val rootPaneBounds = rootPane.bounds
    val point = rootPane.locationOnScreen
    val windowBounds = window.bounds
    window.setLocation(2 * windowBounds.x - point.x, 2 * windowBounds.y - point.y)
    window.setSize(2 * windowBounds.width - rootPaneBounds.width, 2 * windowBounds.height - rootPaneBounds.height)
    if (shouldBeMaximized && window is Frame) {
      window.extendedState = Frame.MAXIMIZED_BOTH
    }
  }

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
      if (showToolWindowImpl(entry = entry, toBeShownInfo = info, dirtyMode = false)) {
        fireStateChanged(ToolWindowManagerEventType.ToolWindowAvailable)
      }
    }
  }

  internal fun toolWindowUnavailable(toolWindow: ToolWindowImpl) {
    val entry = idToEntry.get(toolWindow.id)!!
    val moveFocusAfter = toolWindow.isActive && toolWindow.isVisible
    val info = getRegisteredMutableInfoOrLogError(toolWindow.id)
    executeHide(entry, info, dirtyMode = false, mutation = {
      entry.removeStripeButton()
    })
    fireStateChanged(ToolWindowManagerEventType.ToolWindowUnavailable)
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

    ActivateToolWindowAction.updateToolWindowActionPresentation(toolWindow)
  }

  internal fun activated(toolWindow: ToolWindowImpl, source: ToolWindowEventSource?) {
    val info = getRegisteredMutableInfoOrLogError(toolWindow.id)
    activateToolWindow(entry = idToEntry.get(toolWindow.id)!!, info = info, source = source)
  }

  /**
   * Handles event from decorator and modify weight/floating bounds of the
   * tool window depending on decoration type.
   */
  fun movedOrResized(source: InternalDecoratorImpl) {
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
      val anchor = if (isNewUi) info.anchor else info.anchor
      var another: InternalDecoratorImpl? = null
      val wholeSize = getToolWindowPane(toolWindow).rootPane.size
      if (source.parent is Splitter) {
        var sizeInSplit = if (anchor.isSplitVertically) source.height else source.width
        val splitter = source.parent as Splitter
        if (splitter.secondComponent === source) {
          sizeInSplit += splitter.dividerWidth
          another = splitter.firstComponent as InternalDecoratorImpl
        }
        else {
          another = splitter.secondComponent as InternalDecoratorImpl
        }
        info.sideWeight = getAdjustedRatio(partSize = sizeInSplit,
                                           totalSize = if (anchor.isSplitVertically) splitter.height else splitter.width,
                                           direction = if (splitter.secondComponent === source) -1 else 1)
      }

      val paneWeight = getAdjustedRatio(partSize = if (anchor.isHorizontal) source.height else source.width,
                                        totalSize = if (anchor.isHorizontal) wholeSize.height else wholeSize.width, direction = 1)
      info.weight = paneWeight
      if (another != null) {
        getRegisteredMutableInfoOrLogError(another.toolWindow.id).weight = paneWeight
      }
    }
    fireStateChanged(MovedOrResized)
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
    fireStateChanged(ToolWindowManagerEventType.SetShowStripeButton)
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
}

private enum class KeyState {
  WAITING, PRESSED, RELEASED, HOLD
}

private fun areAllModifiersPressed(@MagicConstant(flagsFromClass = InputEvent::class) modifiers: Int, @MagicConstant(flagsFromClass = InputEvent::class) mask: Int): Boolean {
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
  var baseModifiers = if (SystemInfoRt.isMac) InputEvent.META_MASK else InputEvent.ALT_MASK
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
      balloon.hide()
    }
    listener?.hyperlinkUpdate(e)
  }
}