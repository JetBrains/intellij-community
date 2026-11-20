// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment", "OverridingDeprecatedMember", "ReplaceNegatedIsEmptyWithIsNotEmpty",
               "PrivatePropertyName")

package com.intellij.openapi.wm.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.diagnostic.PluginException
import com.intellij.icons.AllIcons
import com.intellij.ide.UiActivity
import com.intellij.ide.UiActivityMonitor
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.toggleMaximized
import com.intellij.internal.statistic.collectors.fus.actions.persistence.ToolWindowCollector
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ExpirableRunnable
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.MultiProjectToolWindowFactory
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.RegisterToolWindowTaskData
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowBalloonShowOptions
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.openapi.wm.ToolWindowEP
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.WindowInfo
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.ProjectFrameTypeService
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.ex.ToolWindowManagerListener.ToolWindowManagerEventType
import com.intellij.openapi.wm.safeToolWindowPaneId
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.serviceContainer.NonInjectable
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.toolWindow.PreparedRegisterToolWindowTask
import com.intellij.toolWindow.ProjectFrameToolWindowLayoutService
import com.intellij.toolWindow.RegisterToolWindowResult
import com.intellij.toolWindow.ToolWindowButtonManager
import com.intellij.toolWindow.ToolWindowDefaultLayoutManager
import com.intellij.toolWindow.ToolWindowEntry
import com.intellij.toolWindow.ToolWindowEventSource
import com.intellij.toolWindow.ToolWindowFactoryScope
import com.intellij.toolWindow.ToolWindowPane
import com.intellij.toolWindow.ToolWindowPaneNewButtonManager
import com.intellij.toolWindow.ToolWindowPaneOldButtonManager
import com.intellij.toolWindow.ToolWindowProperty
import com.intellij.toolWindow.ToolWindowSetInitializer
import com.intellij.toolWindow.ToolWindowStripeManager
import com.intellij.toolWindow.ToolWindowToolbar
import com.intellij.toolWindow.bringOwnerToFront
import com.intellij.toolWindow.extendedToolWindowsUi.ToolWindowStripeExtension
import com.intellij.toolWindow.findIconFromBean
import com.intellij.toolWindow.getShowingComponentToRequestFocus
import com.intellij.toolWindow.getStripeTitleSupplier
import com.intellij.toolWindow.getToolWindowAnchor
import com.intellij.toolWindow.isUltrawideLayout
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.util.BitUtil
import com.intellij.util.SingleAlarm
import com.intellij.util.SystemProperties
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.SimpleMessageBusConnection
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Component
import java.awt.Frame
import java.awt.Rectangle
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JRootPane
import kotlin.coroutines.EmptyCoroutineContext

private val LOG = logger<ToolWindowManagerImpl>()
private val performShowInSeparateTask = System.getProperty("idea.toolwindow.show.separate.task", "false").toBoolean()

/**
 * Whether the editor area has claimed the focus of the editor that was just closed, because its empty state is that area's focus target.
 *
 * The claimed component is mounted a creation delay later, so focusing a tool window by default here would take focus the empty state is
 * about to be given — and take it visibly, only to lose it again. A claim that ends up with nothing to focus calls
 * [ToolWindowManagerImpl.focusDefaultToolWindow] itself, so standing down cannot leave the frame without a focus owner.
 */
@RequiresEdt
private fun emptyEditorAreaClaimsClosedEditorFocus(project: Project): Boolean {
  val fileEditorManager = project.serviceIfCreated<FileEditorManager>() as? FileEditorManagerImpl ?: return false
  // the init job is awaited because `splitters` reaches `mainSplitters`, which exists only once that job has completed
  return fileEditorManager.initJob.isCompleted && fileEditorManager.splitters.isEmptyStateFocusRequestPending()
}

/** The rank of [anchor] in the layout order of [ToolWindowManagerImpl.findDefaultToolWindowToFocus]. */
private fun getLayoutAnchorRank(anchor: ToolWindowAnchor): Int {
  return when (anchor) {
    ToolWindowAnchor.LEFT -> 0
    ToolWindowAnchor.BOTTOM -> 1
    ToolWindowAnchor.RIGHT -> 2
    else -> 3
  }
}

private typealias Mutation = ((WindowInfoImpl) -> Unit)

@ApiStatus.Internal
open class ToolWindowManagerImpl @NonInjectable @TestOnly internal constructor(
  val project: Project,
  @ApiStatus.Internal @field:JvmField val isNewUi: Boolean,
  private val isEdtRequired: Boolean,
  @JvmField internal val coroutineScope: CoroutineScope,
) : ToolWindowManagerEx(), Disposable {

  private val state: ToolWindowManagerState by lazy(LazyThreadSafetyMode.NONE) { project.service() }

  var layoutState: DesktopLayout
    get() = state.layout
    set(value) {
      state.layout = value
    }

  internal val idToEntry = ConcurrentHashMap<String, ToolWindowEntry>()
  internal val activeStack = ActiveStack()
  private val sideStack = SideStack()
  internal val toolWindowPanes = LinkedHashMap<String, ToolWindowPane>()
  internal var projectFrameTypeId: String? = null
  private val notifications = ToolWindowManagerNotifications(this)
  private val decorators = ToolWindowManagerDecorators(this)

  /** The default pane of a project without a frame of its own, see [initDetachedDefaultPane]. No frame removes it, so [dispose] releases it. */
  @Volatile
  private var detachedDefaultPane: ToolWindowPane? = null

  /**
   * The presentation of the frame owner, when this manager belongs to a frame guest and the frame owner presents its tool windows.
   * `null` for a project with a frame of its own, and for a frame guest without the legacy mode of a tool window,
   * see [ToolWindowFactoryScope.isLegacyToolWindowModeEnabled].
   */
  @Volatile
  internal var frameOwnerPresentation: ToolWindowManagerPresentation? = null
    private set

  /** Presents the tool windows of the frame guests of [project] in the tool windows of this manager. */
  internal val presentation = ToolWindowManagerPresentation(this)

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

  internal var currentState = KeyState.WAITING
  private val waiterForSecondPress: SingleAlarm?
  private val recentToolWindowsState: LinkedList<String>
    get() = state.recentToolWindows

  @Suppress("LeakingThis")
  @VisibleForTesting
  internal val toolWindowSetInitializer: ToolWindowSetInitializer = ToolWindowSetInitializer(project, this)

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
      @Suppress("LeakingThis")
      state.scheduledLayout.afterChange(this) { dl ->
        dl?.let { toolWindowSetInitializer.scheduleSetLayout(it) }
      }
      state.scheduledLayout.get()?.let { toolWindowSetInitializer.scheduleSetLayout(it) }
      // the frame-type layout profile is resolved in `doInit`, once `projectFrameTypeId` is known
    }
  }

  companion object {
    /**
     * Setting this [client property][JComponent.putClientProperty] allows specifying 'effective' parent for a component which will be used
     * to find a tool window to which component belongs (if any). This can prevent tool windows in non-default view modes (e.g. 'Undock')
     *  from closing when focus is transferred to a component not in tool window hierarchy, but logically belonging to it (e.g., when component
     * is added to the window's layered pane).
     *
     * @see ComponentUtil.putClientProperty
     */
    @JvmField
    val PARENT_COMPONENT: Key<JComponent> = Key.create("tool.window.parent.component")

    /**
     * Registers the tool window [toolWindowId] with [component] in a new tool window manager of [project].
     * The manager uses the old stripe buttons and the default layout, and [disposable] disposes it.
     */
    @TestOnly
    @ApiStatus.Internal
    fun registerLocalToolWindowForTest(
      project: Project,
      toolWindowId: String,
      disposable: Disposable,
      component: JComponent = JPanel(),
    ): ToolWindowImpl {
      val paneId = WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
      val buttonManager = ToolWindowPaneOldButtonManager(paneId)
      val manager = object : ToolWindowManagerImpl(
        project = project,
        isNewUi = false,
        isEdtRequired = false,
        coroutineScope = (project as ComponentManagerEx).getCoroutineScope(),
      ) {
        override fun getButtonManager(toolWindow: ToolWindow): ToolWindowButtonManager = buttonManager
      }

      val layoutManager = ToolWindowDefaultLayoutManager(isNewUi = false)
      layoutManager.noStateLoaded()
      manager.setLayoutOnInit(layoutManager.getLayoutCopy())
      Disposer.register(disposable, manager)

      return manager.registerToolWindow(
        task = RegisterToolWindowTaskData(
          id = toolWindowId,
          component = component,
        ),
        buttonManager = buttonManager,
      ).toolWindow
    }

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

    @ApiStatus.Internal
    fun applyAltColors() {
      for (frameHelper in WindowManager.getInstance().allProjectFrames) {
        val manager = getInstance(frameHelper.project ?: continue) as ToolWindowManagerEx
        for (toolwindow in manager.toolWindows) {
          if (toolwindow is ToolWindowImpl) {
            toolwindow.updateContentBackgroundColors()
          }
        }
      }
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
    presentation.dispose()
    detachedDefaultPane?.disposeDetached()
  }

  private fun getDefaultToolWindowPane() = toolWindowPanes.get(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)!!

  @ApiStatus.Internal fun getToolWindowPane(paneId: String): ToolWindowPane = toolWindowPanes.get(paneId) ?: getDefaultToolWindowPane()

  fun getToolWindowPane(toolWindow: ToolWindow): ToolWindowPane {
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

  @VisibleForTesting
  internal open fun getButtonManager(paneId: String): ToolWindowButtonManager = getToolWindowPane(paneId).buttonManager

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

  internal fun revalidateStripeButtons() {
    val buttonManagers = toolWindowPanes.values.mapNotNull { it.buttonManager as? ToolWindowPaneNewButtonManager }
    ApplicationManager.getApplication().invokeLater(ContextAwareRunnable { buttonManagers.forEach { it.refreshUi() } }, project.disposed)
  }

  internal fun createNotInHierarchyIterable(paneId: String): Iterable<Component> {
    return Iterable {
      val ownDecorators = idToEntry.values.asSequence().mapNotNull {
        if (getToolWindowPane(it.toolWindow).paneId == paneId) {
          val component = it.toolWindow.decoratorComponent
          if (component != null && component.parent == null) component else null
        }
        else null
      }
      // a tool window of a frame guest that no tool window of this manager shows now is in no hierarchy
      (ownDecorators + presentation.hiddenPresentedDecorators(paneId)).iterator()
    }
  }

  internal fun updateToolWindowHeaders() {
    @Suppress("DEPRECATION")
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(ExpirableRunnable.forProject(project) {
      for (entry in idToEntry.values) {
        if (entry.readOnlyWindowInfo.isVisible) {
          val decorator = entry.toolWindow.decorator ?: continue
          // The tool window can be split, so we need to update the hover state in all cells.
          val cells = decorator.getOrderedCells()
          for (cell in cells) {
            cell.repaint()
            cell.updateActiveAndHoverState()
          }
        }
      }
      revalidateStripeButtons()
    })
  }

  @Suppress("DEPRECATION")
  internal fun dispatchKeyEvent(e: KeyEvent) {
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

    val project = ProjectUtil.getProjectForComponent(e.component)
    if (project != null && project !== this.project) {
      resetHoldState()
      return
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

  internal fun resetHoldState() {
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

  /**
   * [onDefaultPaneToolWindowsRegistered] runs once on the EDT after the tool windows of the default pane are registered.
   * At this time, [invokeLater] still puts a task in the queue of the pending tasks.
   *
   * With [defersToolWindowRegistration], only the tool windows of [taskListDeferred] are registered now,
   * and every other applicable tool window is registered after the frame content paints, before the pending tasks run.
   */
  internal suspend fun init(
    pane: ToolWindowPane,
    reopeningEditorJob: Job,
    taskListDeferred: Deferred<List<RegisterToolWindowTaskData>>,
    projectFrameTypeId: String? = null,
    onDefaultPaneToolWindowsRegistered: (() -> Unit)? = null,
    defersToolWindowRegistration: Boolean = false,
  ) {
    this.projectFrameTypeId = projectFrameTypeId
    doInit(
      pane = pane,
      connection = project.messageBus.connect(coroutineScope),
      reopeningEditorJob = reopeningEditorJob,
      taskListDeferred = taskListDeferred,
      projectFrameTypeId = projectFrameTypeId,
      onDefaultPaneToolWindowsRegistered = onDefaultPaneToolWindowsRegistered,
      defersToolWindowRegistration = defersToolWindowRegistration,
    )
  }

  /**
   * Waits until the initialization has registered every tool window, the deferred ones included.
   */
  internal suspend fun awaitToolWindowsRegistered() {
    toolWindowSetInitializer.awaitToolWindowsRegistered()
  }

  /**
   * Returns the ids of the tool windows that the stored layout shows: the open ones and the ones with a stripe button.
   * Returns an empty set when no layout is stored.
   */
  internal fun getToolWindowIdsShownByStoredLayout(): Set<String> {
    if (state.noStateLoaded) {
      return emptySet()
    }
    val layout = state.scheduledLayout.get() ?: return emptySet()
    return layout.getInfos().values.asSequence()
      .filter { it.isVisible || it.isShowStripeButton }
      .mapNotNullTo(HashSet()) { it.id }
  }

  /**
   * Tells if the project open may show the tool window [id].
   * Without a stored layout, the open lays out the default one, so it may show any tool window.
   * With a stored layout, the open keeps that layout: it may show [id] only if the stored layout shows it.
   */
  @ApiStatus.Internal
  fun isShownOnStartupAllowed(id: String): Boolean {
    if (state.noStateLoaded) {
      return true
    }
    return state.scheduledLayout.get()?.getInfo(id)?.isVisible == true
  }

  /**
   * Gives a project that has no frame of its own a default pane that no frame shows.
   * A tool window registration then works, and nothing is visible. [frame] is the frame that hosts the project,
   * and [frameOwner] is the project that owns it. The project saves no tool window layout from now on,
   * see [ToolWindowManagerState.becomeFrameGuest].
   *
   * With the legacy mode of a tool window, see [ToolWindowFactoryScope.isLegacyToolWindowModeEnabled], the project registers each tool window
   * of the extension point that shows one project, and the manager of [frameOwner] presents it. The registration runs after this
   * call returns, and a task that [invokeLater] queued runs after it.
   * Without the mode, the tool windows of the extension point are not registered, and such a task is dropped.
   */
  internal suspend fun initDetachedDefaultPane(frame: JFrame, frameOwner: Project) {
    // a Swing component: created on the EDT, as IdeProjectFrameHelper.createCenterComponent creates the pane of a frame
    val pane = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      ToolWindowPane.create(frame, coroutineScope, WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)
    }
    detachedDefaultPane = pane
    state.becomeFrameGuest()
    if (ToolWindowFactoryScope.isLegacyToolWindowModeEnabled) {
      (frameOwner.serviceAsync<ToolWindowManager>() as? ToolWindowManagerImpl)?.let { initAsFrameGuest(it.presentation) }
    }
    doInit(
      pane = pane,
      connection = project.messageBus.connect(coroutineScope),
      reopeningEditorJob = Job().also { it.complete() },
      taskListDeferred = null,
    )
  }

  /**
   * Forwards a call for the presented tool window [toolWindow] to the manager of the frame owner. The frame owner keeps the place,
   * the size and the stripe button of a presented tool window, so a call that reads or changes them goes to its manager.
   * [call] gets the tool window of the frame owner that presents [toolWindow]. A presented tool window that the frame owner
   * does not present now drops the call.
   *
   * @return `true` when [toolWindow] is a presented tool window: the caller must leave the state of this manager alone
   */
  private inline fun forwardToFrameOwner(toolWindow: ToolWindow?, call: ToolWindowManagerImpl.(presenting: ToolWindowImpl) -> Unit): Boolean {
    if (toolWindow !is ToolWindowImpl || !toolWindow.isPresented) {
      return false
    }
    toolWindow.presentedBy?.let { it.toolWindowManager.call(it) }
    return true
  }

  private inline fun forwardToFrameOwner(id: String, call: ToolWindowManagerImpl.(presenting: ToolWindowImpl) -> Unit): Boolean {
    return forwardToFrameOwner(idToEntry.get(id)?.toolWindow, call)
  }

  /**
   * Puts the presented tool window [toolWindow] on the stripe of its project or takes it off, see [ToolWindowImpl.isOnProjectStripe].
   * The manager of the frame owner then gives the project an entry in the project selector or removes the entry.
   */
  private fun setOnProjectStripe(toolWindow: ToolWindowImpl, value: Boolean) {
    if (toolWindow.isOnProjectStripe == value) {
      return
    }
    toolWindow.isOnProjectStripe = value
    frameOwnerPresentation?.availabilityChanged(toolWindow)
  }

  /** A show request brings the stripe button back, see [showToolWindowImpl]. So it brings a presented tool window back to its stripe. */
  private fun putOnProjectStripeForShow(toolWindow: ToolWindowImpl) {
    if (toolWindow.isAvailable) {
      setOnProjectStripe(toolWindow, true)
    }
  }

  /**
   * Tells this manager that its project is a frame guest. From now on, each tool window that it registers without the marker
   * [MultiProjectToolWindowFactory] is a presented tool window, and [frameOwnerPresentation] shows it in the frame of the owner.
   */
  internal fun initAsFrameGuest(frameOwnerPresentation: ToolWindowManagerPresentation) {
    check(frameOwnerPresentation !== presentation) { "A frame owner is no frame guest of itself" }
    this.frameOwnerPresentation = frameOwnerPresentation
  }

  @ApiStatus.Internal
  @VisibleForTesting
  suspend fun doInit(
    pane: ToolWindowPane,
    connection: SimpleMessageBusConnection,
    reopeningEditorJob: Job,
    taskListDeferred: Deferred<List<RegisterToolWindowTaskData>>?,
    projectFrameTypeId: String? = this.projectFrameTypeId,
    onDefaultPaneToolWindowsRegistered: (() -> Unit)? = null,
    defersToolWindowRegistration: Boolean = false,
  ) {
    this.projectFrameTypeId = projectFrameTypeId

    // resolved here rather than in the constructor: the layout profile is keyed by frame type, and
    // `projectFrameTypeId` is only known now. `initUi` below consumes whatever layout we schedule.
    val projectFrameLayoutProfile = resolveProjectFrameToolWindowLayoutProfile(
      projectFrameTypeId = projectFrameTypeId,
      project = project,
      isNewUi = isNewUi,
    )
    if (state.noStateLoaded) {
      // `noStateLoaded` is set exactly when no layout was scheduled, so this cannot overwrite a restored one
      loadDefault(projectFrameLayoutProfile)
    }
    applyProjectFrameLayoutPolicy(projectFrameLayoutProfile) { layout ->
      toolWindowSetInitializer.scheduleSetLayout(layout)
    }

    withContext(ModalityState.any().asContextElement()) {
      val defaultPaneInitialization = launch(Dispatchers.EDT) {
        this@ToolWindowManagerImpl.projectFrame = pane.frame

        // Make sure we haven't already created the root tool window pane.
        // We might have created panes for secondary frames, as they get
        // registered differently, but we shouldn't have the main pane yet
        LOG.assertTrue(!toolWindowPanes.containsKey(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID))

        // This will be the tool window pane for the default frame, which is not automatically added by the ToolWindowPane constructor.
        // If we're reopening other frames, their tool window panes will be added,
        // but we still need to initialize the tool windows themselves.
        toolWindowPanes.put(pane.paneId, pane)
      }
      defaultPaneInitialization.join()
      val frameOwnerPresentation = frameOwnerPresentation
      if (frameOwnerPresentation == null) {
        toolWindowSetInitializer.initUi(
          reopeningEditorJob = reopeningEditorJob,
          taskListDeferred = taskListDeferred,
          onDefaultPaneToolWindowsRegistered = onDefaultPaneToolWindowsRegistered,
          defersRegistration = defersToolWindowRegistration,
        )
      }
      else {
        // not awaited: the open of a frame guest restores its editors after this call, and the registration waits for the editors
        coroutineScope.launch(CoroutineName("toolwindow registration of a frame guest") + ModalityState.any().asContextElement()) {
          toolWindowSetInitializer.initPresentedUi(frameOwnerPresentation)
        }
      }
    }

    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        if (!ClientId.isCurrentlyUnderLocalId) {
          return
        }

        coroutineScope.launch(Dispatchers.EDT) {
          @Suppress("DEPRECATION")
          focusManager.doWhenFocusSettlesDown(ExpirableRunnable.forProject(project) {
            if (!FileEditorManager.getInstance(project).hasOpenFiles()) {
              focusDefaultToolWindow(emptyStateClaimsFocus = emptyEditorAreaClaimsClosedEditorFocus(project))
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
    if (isToolWindowRegistrationSuppressed(bean.id)) {
      return
    }

    val condition = bean.getCondition(plugin)
    if (condition != null && !condition.value(project)) {
      return
    }

    val factory = bean.getToolWindowFactory(bean.pluginDescriptor)
    if (!ToolWindowFactoryScope.of(project).accepts(factory) || !factory.isApplicableAsync(project)) {
      return
    }

    withContext(Dispatchers.EDT) {
      // always add to the default tool window pane
      @Suppress("DEPRECATION")
      val task = RegisterToolWindowTaskData(
        id = bean.id,
        icon = findIconFromBean(bean, factory, plugin),
        anchor = getToolWindowAnchor(factory, bean),
        sideTool = bean.secondary || bean.side,
        canCloseContent = bean.canCloseContents,
        shouldBeAvailable = factory.shouldBeAvailable(project),
        contentFactory = factory,
        stripeTitle = getStripeTitleSupplier(id = bean.id, project = project, pluginDescriptor = plugin),
        pluginDescriptor = plugin,
      )

      val toolWindowPane = getDefaultToolWindowPaneIfInitialized()
      registerToolWindow(task, toolWindowPane.buttonManager)

      toolWindowPane.buttonManager.getStripeFor(task.anchor, task.sideTool).revalidate()
      toolWindowPane.validate()
      toolWindowPane.repaint()
    }
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowsRegistered(listOf(bean.id), this)
  }

  internal fun isToolWindowRegistrationSuppressed(toolWindowId: String): Boolean {
    return service<ProjectFrameToolWindowLayoutService>().isToolWindowRegistrationSuppressed(
      profileId = getProjectFrameToolWindowLayoutProfileId(),
      toolWindowId = toolWindowId,
    )
  }

  internal fun getSuppressedToolWindowIds(): Set<String> {
    return service<ProjectFrameToolWindowLayoutService>().getSuppressedToolWindowIds(getProjectFrameToolWindowLayoutProfileId())
  }

  private fun getProjectFrameToolWindowLayoutProfileId(): String? {
    // a frame guest is in the frame of its owner, so the frame type of the owner decides
    val frameOwnerPresentation = frameOwnerPresentation
    val frameTypeId = if (frameOwnerPresentation == null) projectFrameTypeId else frameOwnerPresentation.projectFrameTypeId
    return service<ProjectFrameTypeService>().getToolWindowLayoutProfileId(frameTypeId)
  }

  private fun getDefaultToolWindowPaneIfInitialized(): ToolWindowPane {
    return toolWindowPanes.get(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)
           ?: throw IllegalStateException("You must not register toolwindow programmatically so early. " +
                                          "Rework code or use ToolWindowManager.invokeLater")
  }

  /**
   * A frame guest leaves its frame at this event, so the frame owner stops to present the tool windows of the guest.
   * A tool window that the guest registers after the event is not presented.
   */
  internal fun projectClosing() {
    val presentation = frameOwnerPresentation ?: return
    frameOwnerPresentation = null
    for (entry in idToEntry.values) {
      if (entry.toolWindow.isPresented) {
        presentation.detach(entry.toolWindow)
      }
    }
  }

  internal fun projectClosed() {
    // hide everything outside the frame (floating and windowed) - frame contents are handled separately elsewhere
    for (entry in idToEntry.values) {
      if (entry.toolWindow.windowInfo.type.isInternal) {
        continue
      }

      try {
        decorators.removeExternalDecorators(entry)
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

  private fun loadDefault(projectFrameLayoutProfile: ProjectFrameToolWindowLayoutProfile?) {
    val layout = projectFrameLayoutProfile?.profile?.layout ?: ToolWindowDefaultLayoutManager.getInstance().getLayoutCopy()
    toolWindowSetInitializer.scheduleSetLayout(layout)
  }

  override fun activateEditorComponent() {
    EditorsSplitters.focusDefaultComponentInSplittersIfPresent(project)
  }

  @RequiresEdt
  open fun activateToolWindow(id: String, runnable: Runnable?, autoFocusContents: Boolean, source: ToolWindowEventSource? = null) {
    val activity = UiActivity.Focus("toolWindow:$id")
    UiActivityMonitor.getInstance().addActivity(project, activity, ModalityState.nonModal())

    val entry = idToEntry.get(id)
    if (entry == null) {
      LOG.error(Throwable("No entry found for the tool window $id"))
      return
    }
    activateToolWindow(entry = entry,
                       info = getRegisteredMutableInfoOrLogError(id),
                       autoFocusContents = autoFocusContents,
                       source = source)

    coroutineScope.launch(Dispatchers.EDT) {
      //maybe readAction
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

    if (entry.toolWindow.isPresented) {
      putOnProjectStripeForShow(entry.toolWindow)
      frameOwnerPresentation?.activate(entry.toolWindow, autoFocusContents, source)
      return
    }

    if (isUnifiedToolWindowSizesEnabled() && canUseUnifiedWeight(info)) {
      info.weight = layoutState.getUnifiedAnchorWeight(info.anchor)
      LOG.debug { "Activated tool window: ${info.id}, using ${info.anchor} unified weight of ${info.weight}" }
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
      showToolWindowImpl(entry = entry, toBeShownInfo = info, dirtyMode = false, source = source)
    }
    else if (!autoFocusContents /* if focus is requested, focusing code will do this */ && !info.type.isInternal) {
      bringOwnerToFront(toolWindow = entry.toolWindow, focus = false)
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

  private fun canUseUnifiedWeight(info: WindowInfoImpl): Boolean {
    if (!info.isDocked || !info.anchor.isUltrawideLayout()) {
      return true
    }
    return layoutState.getInfos().values.none {
      it.id != info.id &&
      it.isVisible &&
      it.isDocked &&
      it.safeToolWindowPaneId == info.safeToolWindowPaneId &&
      it.anchor == info.anchor &&
      it.isSplit != info.isSplit
    }
  }

  private fun isIndependentToolWindowResizeEnabled(): Boolean {
    return if (isNewUi) {
      UISettings.getInstance().rememberSizeForEachToolWindowNewUI
    }
    else {
      UISettings.getInstance().rememberSizeForEachToolWindowOldUI
    }
  }

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
  @ApiStatus.Internal
  fun getRegisteredMutableInfoOrLogError(id: String): WindowInfoImpl {
    val presented = idToEntry.get(id)?.takeIf { it.toolWindow.isPresented }
    if (presented != null) {
      // The frame owner keeps the place and the visible state of a presented tool window. So its info is a copy that never
      // goes into the layout of the frame guest, and the layout that the guest stored for a frame of its own stays.
      return (presented.readOnlyWindowInfo as WindowInfoImpl).copy()
    }

    var info = layoutState.getInfo(id)
    if (info == null) {
      val entry = idToEntry.get(id) ?: throw IllegalStateException("window with id=\"$id\" isn't registered")
      // window was registered but stripe button was not shown, so, layout was not added to a list
      info = (entry.readOnlyWindowInfo as WindowInfoImpl).copy()
      layoutState.addInfo(id, info)
    }
    return info
  }

  internal fun deactivateToolWindow(
    info: WindowInfoImpl,
    entry: ToolWindowEntry,
    dirtyMode: Boolean = false,
    mutation: Mutation? = null,
    source: ToolWindowEventSource? = null,
  ) {
    LOG.debug { "deactivateToolWindow(${info.id})" }

    setHiddenState(info, entry, source)
    mutation?.invoke(info)
    decorators.updateStateAndRemoveDecorator(info, entry, dirtyMode = dirtyMode)

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

  override fun getIdsOn(anchor: ToolWindowAnchor): List<String> {
    return getVisibleToolWindowsOn(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID, anchor).map { it.id }.toList()
  }

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

  override fun getShowInFindToolWindowIcon(): Icon {
    return if (isNewUi) AllIcons.General.OpenInToolWindow else getLocationIcon(ToolWindowId.FIND, AllIcons.General.Pin_tab)
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
    if (entry.toolWindow.isPresented) {
      putOnProjectStripeForShow(entry.toolWindow)
      frameOwnerPresentation?.show(entry.toolWindow)
      return
    }
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
    if (entry.toolWindow.isPresented) {
      frameOwnerPresentation?.hide(entry.toolWindow, hideSide, moveFocus, removeFromStripe, source)
      if (removeFromStripe && source == null) {
        // the request of the code, as after the removal of the last content: the stripe button of the frame owner is for the user
        setOnProjectStripe(entry.toolWindow, false)
      }
      return
    }
    if (removeFromStripe && source == null && presentation.ownerLeavesStripe(entry.toolWindow)) {
      // the request of the code, as after the removal of the last content: a frame guest still needs the stripe button
      return
    }
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
          // SideStack contains copies of real WindowInfos.
          // It means that this stored info can be invalid. The following loop removes invalid WindowInfos.
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
      toBeShownInfo.order = layoutState.getNextOrder(toBeShownInfo.safeToolWindowPaneId, toBeShownInfo.anchor)
    }

    val snapshotInfo = toBeShownInfo.copy()
    entry.applyWindowInfo(snapshotInfo)
    doShowWindow(entry, snapshotInfo, dirtyMode)

    return true
  }

  private fun doShowWindow(entry: ToolWindowEntry, info: WindowInfo, dirtyMode: Boolean) {
    LOG.debug { "Showing the tool window ${info.id}" }
    if (entry.readOnlyWindowInfo.type == ToolWindowType.FLOATING) {
      decorators.addFloatingDecorator(entry, info)
    }
    else if (entry.readOnlyWindowInfo.type == ToolWindowType.WINDOWED) {
      decorators.addWindowedDecorator(entry, info)
    }
    else {
      // Docked and sliding windows
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
            toolWindowPane.removeDecorator(info = otherInfoCopy, component = decorator, dirtyMode = false, manager = this)
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
        toolWindowPane.addDecorator(
          decorator = entry.toolWindow.getOrCreateDecoratorComponent(),
          info = info,
          dirtyMode = dirtyMode,
          manager = this,
        )
      }

      // remove a tool window from the SideStack
      if (isStackEnabled) {
        sideStack.remove(entry.id)
      }
    }

    if (entry.stripeButton == null) {
      val buttonManager = getButtonManager(entry.toolWindow)
      entry.stripeButton = buttonManager.createStripeButton(toolWindow = entry.toolWindow, info = info, task = null)
    }

    entry.toolWindow.scheduleContentInitializationIfNeeded()
    fireToolWindowShown(entry.toolWindow)
  }

  override fun registerToolWindow(task: RegisterToolWindowTask): ToolWindow {
    ThreadingAssertions.assertEventDispatchThread()

    // a frame guest registered the id first, and this frame owner put content into the tool window that presents the frame guests
    presentation.placeholderWithOwnerContent(task.id)?.let { return it.toolWindow }
    if (isPresentedRegistration(task.data)) {
      return registerPresentedToolWindow(task.data)
    }

    // try to get a previously saved tool window pane, if possible
    val toolWindowPane = layoutState.getInfo(task.id)?.toolWindowPaneId?.let { getToolWindowPane(it) }
                         ?: getDefaultToolWindowPaneIfInitialized()
    val entry = registerToolWindow(task.data, buttonManager = toolWindowPane.buttonManager)
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowsRegistered(listOf(entry.id), this)

    toolWindowPane.buttonManager.getStripeFor(entry.toolWindow.anchor, entry.toolWindow.isSplitMode).revalidate()

    toolWindowPane.validate()
    toolWindowPane.repaint()
    fireStateChanged(ToolWindowManagerEventType.RegisterToolWindow, entry.toolWindow)
    return entry.toolWindow
  }

  /**
   * Tells whether the tool window [task] of this manager is a presented one: this manager belongs to a frame guest, and the
   * factory has no marker [MultiProjectToolWindowFactory]. The rule is the same for the extension point and for the code of
   * a frame guest, which knows one project. A frame owner and a project with a frame of its own present no tool window of their own.
   */
  private fun isPresentedRegistration(task: RegisterToolWindowTaskData): Boolean {
    return frameOwnerPresentation != null && task.contentFactory !is MultiProjectToolWindowFactory
  }

  /**
   * The tool windows of this manager show the project of the manager, not the active project of the frame: the legacy mode
   * of a tool window is on, and the project shows tool window content of its own. So the data context of such a tool window
   * has this project. A tool window with the marker [MultiProjectToolWindowFactory] is the exception, see
   * [ToolWindowImpl.dataContextProject].
   */
  internal val showsOwnProject: Boolean
    get() = ToolWindowFactoryScope.isLegacyToolWindowModeEnabled && !suppressesOwnContent

  /** The frame of this project shows no tool window content of the project, see [ProjectFrameCapability.SUPPRESS_OWN_TOOL_WINDOW_CONTENT]. */
  internal val suppressesOwnContent: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
    service<ProjectFrameCapabilitiesService>().has(project, ProjectFrameCapability.SUPPRESS_OWN_TOOL_WINDOW_CONTENT)
  }

  /**
   * Registers the tool window [task] that the code of this frame guest asks for, and that the frame owner presents.
   * A presented tool window has no stripe button, so the call changes no pane.
   */
  private fun registerPresentedToolWindow(task: RegisterToolWindowTaskData): ToolWindow {
    val entry = registerToolWindow(task, getButtonManager(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID))
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowsRegistered(listOf(entry.id), this)
    fireStateChanged(ToolWindowManagerEventType.RegisterToolWindow, entry.toolWindow)
    return entry.toolWindow
  }

  internal fun registerToolWindow(task: RegisterToolWindowTaskData, buttonManager: ToolWindowButtonManager): ToolWindowEntry {
    // a frame guest can register the id first: the tool window of this frame owner then takes the place of the one that presents the guests
    return presentation.registerForOwner(task.id) {
      val result = registerToolWindow(
        preparedTask = prepareRegisterToolWindowTask(task),
        buttonManager = buttonManager,
        layout = layoutState,
        ensureToolWindowActionRegistered = true,
      )
      result.postTask?.invoke()
      attachPresented(result.entry, task)
      result.entry
    }
  }

  /**
   * Gives the presented tool window [entry] of this frame guest to the manager of the frame owner, which shows it from now on.
   * [task] is the registration of [entry]. The call does nothing for a tool window that is not presented.
   */
  internal fun attachPresented(entry: ToolWindowEntry, task: RegisterToolWindowTaskData) {
    if (entry.toolWindow.isPresented) {
      frameOwnerPresentation?.attach(entry.toolWindow, task)
    }
  }

  /**
   * Registers a tool window that has no content of its own and presents the tool windows of the frame guests with the id of
   * [task], see [ToolWindowManagerPresentation], which makes [task]. The tool window is not available at first.
   * It shows with the stored visible state when a frame guest gets an entry.
   */
  internal fun registerPresentingToolWindow(task: RegisterToolWindowTaskData): ToolWindowEntry {
    val paneId = layoutState.getInfo(task.id)?.safeToolWindowPaneId ?: WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
    val entry = registerToolWindow(task, getButtonManager(paneId))
    project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowsRegistered(listOf(entry.id), this)
    // a test has no pane
    toolWindowPanes.get(paneId)?.let { toolWindowPane ->
      toolWindowPane.buttonManager.getStripeFor(entry.toolWindow.anchor, entry.toolWindow.isSplitMode).revalidate()
      toolWindowPane.validate()
      toolWindowPane.repaint()
    }
    fireStateChanged(ToolWindowManagerEventType.RegisterToolWindow, entry.toolWindow)
    return entry
  }

  /**
   * Sets the state of the presented tool window [id] of this frame guest: [visible], and the place of the presenting tool window
   * [place]. Only [ToolWindowManagerPresentation] calls it. A presented tool window is in no pane, so the call changes no pane.
   * The state is only in the tool window, not in the layout of this frame guest, see [getRegisteredMutableInfoOrLogError].
   */
  internal fun applyPresented(id: String, visible: Boolean, place: WindowInfo) {
    val entry = idToEntry.get(id) ?: return
    val current = entry.readOnlyWindowInfo
    val wasVisible = current.isVisible
    val isSamePlace = current.anchor == place.anchor && current.type == place.type && current.internalType == place.internalType &&
                      current.isSplit == place.isSplit && current.isAutoHide == place.isAutoHide
    if (wasVisible == visible && isSamePlace) {
      return
    }

    val info = getRegisteredMutableInfoOrLogError(id)
    info.anchor = place.anchor
    info.type = place.type
    info.internalType = place.internalType
    info.isSplit = place.isSplit
    info.isAutoHide = place.isAutoHide
    info.isVisible = visible
    if (!visible) {
      info.isActiveOnStart = false
      activeStack.remove(entry, false)
    }
    entry.applyWindowInfo(info.copy())
    if (wasVisible == visible) {
      return
    }

    if (visible) {
      entry.toolWindow.scheduleContentInitializationIfNeeded()
      fireToolWindowShown(entry.toolWindow)
      fireStateChanged(ToolWindowManagerEventType.ShowToolWindow, entry.toolWindow)
    }
    else {
      fireStateChanged(ToolWindowManagerEventType.HideToolWindow, entry.toolWindow)
    }
  }

  /**
   * Prepares [task] for the registration in the current layout.
   * With the new UI, a tool window that the layout lacks gets its factory-default entry, and a registered tool window whose entry
   * moved for it gets the new entry.
   */
  internal fun prepareRegisterToolWindowTask(task: RegisterToolWindowTaskData): PreparedRegisterToolWindowTask {
    if (isPresentedRegistration(task)) {
      // the frame owner keeps the place, the stripe button and the visible state of a presented tool window
      return PreparedRegisterToolWindowTask(
        task = task,
        isButtonNeeded = false,
        existingInfo = null,
        paneId = WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID,
        isPresented = true,
      )
    }

    val layout = layoutState
    if (isNewUi && layout.getInfo(task.id) == null && task.id in ToolWindowDefaultLayoutManager.getInstance().getFactoryDefaultSeedIds()) {
      val factoryDefault = ToolWindowDefaultLayoutManager.getInstance().getFactoryDefaultLayoutCopy()
      for (otherInfo in seedFromFactoryDefault(layout = layout, ids = listOf(task.id), factoryDefault = factoryDefault)) {
        val otherId = otherInfo.id ?: continue
        if (otherId != task.id) {
          idToEntry.get(otherId)?.toolWindow?.setWindowInfoSilently(otherInfo.copy())
        }
      }
    }
    val existingInfo = layout.getInfo(task.id)
    return PreparedRegisterToolWindowTask(
      task = task,
      isButtonNeeded = isButtonNeeded(task, existingInfo, project.service<ToolWindowStripeManager>()),
      existingInfo = existingInfo,
      paneId = existingInfo?.safeToolWindowPaneId ?: WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID,
    )
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
        info.order = layout.getNextOrder(paneId = info.safeToolWindowPaneId, anchor = task.anchor)
        layout.addInfo(task.id, info)
      }
    }

    // The new UI (without a ToolWindowStripeExtension) does not have top stripes, and their presence is considered an error
    if (isNewUi && info.anchor == ToolWindowAnchor.TOP && !ToolWindowStripeExtension.exists) {
      info.anchor = ToolWindowAnchor.LEFT
    }

    val disposable = Disposer.newDisposable(task.id)
    Disposer.register(this, disposable)

    val factory = task.contentFactory

    val infoSnapshot = info.copy()
    if (infoSnapshot.isVisible && (factory == null || !task.shouldBeAvailable)) {
      // isVisible cannot be true if contentFactory is null, because we cannot show toolwindow without content
      infoSnapshot.isVisible = false
    }

    val toolWindow = ToolWindowImpl(
      toolWindowManager = this,
      id = task.id,
      canCloseContent = task.canCloseContent,
      dumbAware = task.canWorkInDumbMode,
      component = task.component,
      parentDisposable = disposable,
      windowInfo = infoSnapshot,
      contentFactory = factory,
      isAvailable = task.shouldBeAvailable,
      stripeTitleProvider = task.stripeTitle ?: Supplier {
        @Suppress("HardCodedStringLiteral")
        task.id
      },
      isPresented = preparedTask.isPresented,
      isMultiProject = factory is MultiProjectToolWindowFactory,
    )
    toolWindow.stripeButtonVisibilityDefault = shouldShowStripeButtonByDefault(task.id, task.contentFactory, task.pluginDescriptor, isNewUi)
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

      (task.pluginDescriptor?.pluginClassLoader?.let { (project as ComponentManagerEx).pluginCoroutineScope(it) } ?: coroutineScope).launch {
        factory.manage(toolWindow = toolWindow, toolWindowManager = this@ToolWindowManagerImpl)
      }.cancelOnDispose(toolWindow.disposable)
    }

    // contentFactory.init can set icon
    if (toolWindow.icon == null) {
      task.icon?.let {
        toolWindow.doSetIcon(it)
      }
    }

    // the activation action is one for the application, and the tool window of the frame owner registers it
    if (ensureToolWindowActionRegistered && !preparedTask.isPresented) {
      ActivateToolWindowAction.Manager.ensureToolWindowActionRegistered(toolWindow, ActionManager.getInstance())
    }

    val stripeButton = if (preparedTask.isButtonNeeded) {
      // a batch registration, which registers the activation actions later, refreshes its buttons after them
      buttonManager.createStripeButton(
        toolWindow = toolWindow,
        info = infoSnapshot,
        task = RegisterToolWindowTask(task),
        isRefreshedAfterRegistration = !ensureToolWindowActionRegistered,
      )
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
    // mode. But if a tool window was active but its mode doesn't allow activating it again
    // (for example, a tool window is in auto hide mode), then we just activate an editor component.
    if (stripeButton != null && factory != null /* not null on an init tool window from EP */ && infoSnapshot.isVisible) {
      val postTask = {
        // no need to use dirtyMode=true as this post-task executed on own UI task after all tool windows are registered
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

  internal fun isButtonNeeded(task: RegisterToolWindowTaskData, info: WindowInfoImpl?, stripeManager: ToolWindowStripeManager): Boolean {
    return (task.shouldBeAvailable
            && (info?.isShowStripeButton ?: shouldShowStripeButtonByDefault(task.id, task.contentFactory, task.pluginDescriptor, isNewUi))
            && stripeManager.allowToShowOnStripe(task.id, info == null, isNewUi))
  }

  @Deprecated("Use ToolWindowFactory and toolWindow extension point")
  @Suppress("OverridingDeprecatedMember")
  override fun unregisterToolWindow(id: String) {
    val toolWindow = idToEntry.get(id)?.toolWindow
    doUnregisterToolWindow(id)
    fireStateChanged(ToolWindowManagerEventType.UnregisterToolWindow, toolWindow)
  }

  internal fun doUnregisterToolWindow(id: String) {
    LOG.debug { "unregisterToolWindow($id)" }

    ThreadingAssertions.assertEventDispatchThread()
    // the activation action of a presented tool window belongs to the tool window of the frame owner
    if (idToEntry.get(id)?.toolWindow?.isPresented != true) {
      ActivateToolWindowAction.Manager.unregister(id)
    }

    val entry = idToEntry.remove(id) ?: return
    // the frame guests that the tool window presented
    var orphanSlot: PresentingSlot? = null
    try {
      val toolWindow = entry.toolWindow

      val info = layoutState.getInfo(id)
      if (toolWindow.isPresented) {
        // a presented tool window is in no pane and has no stripe button
        frameOwnerPresentation?.detach(toolWindow)
        activeStack.remove(entry, true)
      }
      else {
        orphanSlot = presentation.presentingUnregistering(toolWindow)
      }
      if (!toolWindow.isPresented && info != null) {
        // remove decorator and tool button from the screen - removing will also save current bounds
        decorators.updateStateAndRemoveDecorator(info, entry, false)
        // save recent appearance of a tool window
        activeStack.remove(entry, true)
        if (isStackEnabled) {
          sideStack.remove(id)
        }
        entry.removeStripeButton()
        // a test has no pane
        if (toolWindowPanes.containsKey(WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID)) {
          val toolWindowPane = getToolWindowPane(info.safeToolWindowPaneId)
          toolWindowPane.validate()
          toolWindowPane.repaint()
        }
      }

      if (!project.isDisposed) {
        project.messageBus.syncPublisher(ToolWindowManagerListener.TOPIC).toolWindowUnregistered(id, (toolWindow))
      }
    }
    finally {
      // the entry is no longer in idToEntry, so nothing else disposes it
      Disposer.dispose(entry.disposable)
    }
    // after the disposal: the frame guests get a new tool window with the id
    orphanSlot?.let(presentation::presentingUnregistered)
  }

  internal fun saveFloatingOrWindowedState(entry: ToolWindowEntry, info: WindowInfoImpl) {
    decorators.saveFloatingOrWindowedState(entry, info)
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
    LOG.debug { "The initial tool window layout is $newLayout" }
    layoutState = newLayout
  }

  override fun setLayout(newLayout: DesktopLayout) {
    ThreadingAssertions.assertEventDispatchThread()
    LOG.debug { "Restoring layout $newLayout" }

    if (idToEntry.isEmpty()) {
      LOG.debug { "The current layout is empty, not applying anything" }
      layoutState = newLayout
      return
    }

    val factoryDefault = ToolWindowDefaultLayoutManager.getInstance().getFactoryDefaultLayoutCopy()

    data class LayoutData(val old: WindowInfoImpl, val new: WindowInfoImpl, val entry: ToolWindowEntry)

    val list = mutableListOf<LayoutData>()

    for (entry in idToEntry.values) {
      if (entry.toolWindow.isPresented) {
        // the layout of the frame owner decides the place and the visible state of a presented tool window
        continue
      }
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
          LOG.debug {
            "The window ${old.id} doesn't exist in the new layout, making it and its stripe button invisible, its new state will be $new"
          }
        }
        else {
          LOG.debug {
            "The window ${old.id} doesn't exist in the new layout, resetting it to the default, its new state will be $new"
          }
        }
        newLayout.addInfo(entry.id, new)
      }
      if (old != new) {
        if (!entry.toolWindow.isAvailable) {
          new.isVisible = false // Preserve invariants: if it can't be shown, don't consider it visible.
          LOG.debug { "The window ${old.id} isn't currently available, making it invisible, its new state will be $new" }
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
        LOG.debug { "Hiding the tool window ${item.entry.id} as it's invisible in the new layout" }
        decorators.updateStateAndRemoveDecorator(state = item.new, entry = item.entry, dirtyMode = true)
      }

      if (item.old.safeToolWindowPaneId != item.new.safeToolWindowPaneId
          || item.old.anchor != item.new.anchor
          || item.old.order != item.new.order
          || item.old.isShowStripeButton != item.new.isShowStripeButton
      ) {
        LOG.debug {
          "Updating the anchor of the tool window ${item.entry.id} because one of the following has changed:" +
          " pane (old=${item.old.safeToolWindowPaneId} new=${item.new.safeToolWindowPaneId})," +
          " anchor (old=${item.old.anchor} new=${item.new.anchor})," +
          " order (old=${item.old.order} new=${item.new.order})," +
          " isShowStripeButton (old=${item.old.isShowStripeButton} new=${item.new.isShowStripeButton})"
        }
        setToolWindowAnchorImpl(
          entry = item.entry,
          currentInfo = item.old,
          layoutInfo = item.new,
          paneId = item.new.safeToolWindowPaneId,
          anchor = item.new.anchor,
          order = item.new.order,
          layoutState = null,
        )
      }

      var toShowWindow = false

      if (item.old.isSplit != item.new.isSplit && item.old.type.isInternal && item.new.type.isInternal) {
        val wasVisible = item.old.isVisible
        // we should hide the window and show it in a 'new place'
        // to automatically hide a possible window already located in a 'new place'
        if (wasVisible) {
          LOG.debug {
            "Temporarily hiding the tool window ${item.entry.id} because one of the following is changed:" +
            " isSplit (old=${item.old.isSplit} new=${item.new.isSplit})," +
            " isInternal (old=${item.old.type.isInternal} new=${item.new.type.isInternal})"
          }
          hideToolWindow(item.entry.id)
          toShowWindow = true
        }
      }

      if (item.old.type != item.new.type) {
        val dirtyMode = item.old.type.isInternal
        LOG.debug {
          "Saving the tool window ${item.entry.id} state and removing its decorators " +
          "as its type has changed: ${item.old.type} -> ${item.new.type}, " +
          "will ${if (item.new.isVisible) "re-show" else "NOT re-show"} it again later"
        }
        decorators.updateStateAndRemoveDecorator(item.old, item.entry, dirtyMode)
        if (item.new.isVisible) {
          toShowWindow = true
        }
      }
      else if (!item.old.isVisible && item.new.isVisible) {
        LOG.debug { "The tool window ${item.entry.id} will become visible in the new layout" }
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
          LOG.debug { "Changing the tool window ${item.entry.id} weight from ${item.old.weight} to ${item.new.weight}" }
          val anchor = item.new.anchor
          var weight = item.new.weight
          val another = list.firstOrNull { it !== item && it.new.anchor == anchor && it.new.isVisible && it.new.isDocked }
          if (another != null && anchor.isUltrawideLayout()) { // split windows side-by-side, set weight of the entire splitter
            weight += another.new.weight
            LOG.debug {
              "Adding to the tool window ${item.entry.id} weight " +
              "the weight of ${another.entry.id} (${another.new.weight}) " +
              "because they're displayed side-by-side in the ultrawide layout"
            }
          }
          LOG.debug { "Setting ${item.entry.id} weight=${weight} from the saved layout" }
          toolWindowPane.setWeight(anchor, weight)
        }
        if (item.old.sideWeight != item.new.sideWeight) {
          LOG.debug { "Setting ${item.entry.id} side weight ${item.new.sideWeight} from the saved layout" }
          toolWindowPane.setSideWeight(item.entry.toolWindow, item.new.sideWeight)
        }
      }
    }

    LOG.debug("PASS 3: revalidate/repaint panes")
    val rootPanes = HashSet<JRootPane>()
    for (layoutData in list) {
      getToolWindowPane(layoutData.entry.toolWindow).let { toolWindowPane ->
        toolWindowPane.buttonManager.revalidateNotEmptyStripes()
        toolWindowPane.validate()
        toolWindowPane.repaint()
        toolWindowPane.frame.rootPane?.let { rootPanes.add(it) }
      }
    }

    activateEditorComponent()

    for (rootPane in rootPanes) {
      rootPane.revalidate()
      rootPane.repaint()
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
      val startUpContextElement = FUSProjectHotStartUpMeasurer.getStartUpContextElementToPass() ?: EmptyCoroutineContext
      coroutineScope.launch(Dispatchers.UiWithModelAccess + ModalityState.nonModal().asContextElement() + startUpContextElement) {
        runnable.run()
      }
    }
  }

  override val focusManager: IdeFocusManager
    get() = IdeFocusManager.getGlobalInstance()

  override fun canShowNotification(toolWindowId: String): Boolean = notifications.canShowNotification(toolWindowId)

  override fun notifyByBalloon(options: ToolWindowBalloonShowOptions) {
    notifications.notifyByBalloon(options)
  }

  fun getToolWindowButton(toolWindowId: String): JComponent? = notifications.getToolWindowButton(toolWindowId)

  @RequiresEdt
  override fun closeBalloons() {
    notifications.closeBalloons()
  }

  override fun getToolWindowBalloon(id: String): Balloon? = notifications.getToolWindowBalloon(id)

  override val isEditorComponentActive: Boolean
    get() = state.isEditorComponentActive

  @RequiresEdt
  fun setToolWindowAnchor(id: String, anchor: ToolWindowAnchor) {
    setToolWindowAnchor(id = id, anchor = anchor, order = -1)
  }

  // used by Rider
  @Suppress("MemberVisibilityCanBePrivate")
  fun setToolWindowAnchor(id: String, anchor: ToolWindowAnchor, order: Int) {
    if (forwardToFrameOwner(id) { setToolWindowAnchor(id, anchor, order) }) {
      return
    }
    val entry = idToEntry.get(id)!!

    val info = entry.readOnlyWindowInfo
    if (anchor == info.anchor && (order == info.order || order == -1)) {
      return
    }

    ThreadingAssertions.assertEventDispatchThread()
    setToolWindowAnchorImpl(
      entry = entry,
      currentInfo = info,
      layoutInfo = getRegisteredMutableInfoOrLogError(id),
      paneId = info.safeToolWindowPaneId,
      anchor = anchor,
      order = order,
      layoutState = layoutState,
    )
    getToolWindowPane(info.safeToolWindowPaneId).validateAndRepaint()
    fireStateChanged(ToolWindowManagerEventType.SetToolWindowAnchor, entry.toolWindow)
  }

  fun setVisibleOnLargeStripe(id: String, visible: Boolean) {
    if (forwardToFrameOwner(id) { }) {
      // the frame owner keeps the stripe button
      return
    }
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
      doSetAnchor(
        entry = entry,
        info = layoutInfo,
        paneId = paneId,
        anchor = anchor,
        order = order,
        currentInfo = currentInfo,
        layoutState = layoutState,
      )
    }
    else {
      val wasFocused = entry.toolWindow.isActive
      // for docked and sliding windows, we have to move buttons and window's decorators
      layoutInfo.isVisible = false
      toolWindowPane.removeDecorator(info = currentInfo, component = entry.toolWindow.decoratorComponent, dirtyMode = true, manager = this)

      doSetAnchor(
        entry = entry,
        info = layoutInfo,
        paneId = paneId,
        anchor = anchor,
        order = order,
        currentInfo = currentInfo,
        layoutState = layoutState,
      )

      showToolWindowImpl(entry = entry, toBeShownInfo = layoutInfo, dirtyMode = false)
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
      LOG.debug { "Updating order of the affected windows because ${entry.id} has changed anchor" }
      for (otherInfo in layoutState.setAnchor(info, paneId, anchor, order)) {
        val otherToolWindow = idToEntry.get(otherInfo.id ?: continue)?.toolWindow ?: continue
        LOG.debug { "Affected tool window ${otherInfo.id}: ${windowInfoChanges(otherToolWindow.windowInfo, otherInfo)}" }
        otherToolWindow.setWindowInfoSilently(otherInfo.copy())
      }
    }

    entry.toolWindow.applyWindowInfo(info.copy())
    // A safety check: if the tool window is visible, we ignore isShowStripeButton.
    if (info.isShowStripeButton || info.isVisible) {
      entry.stripeButton = getToolWindowPane(paneId).buttonManager.createStripeButton(
        toolWindow = entry.toolWindow,
        info = info,
        task = null,
      )
    }
  }

  internal fun setSideTool(id: String, isSplit: Boolean) {
    if (forwardToFrameOwner(id) { setSideTool(id, isSplit) }) {
      return
    }
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
    // to automatically hide a possible window already located in a 'new place'
    hideIfNeededAndShowAfterTask(entry, info) {
      info.isSplit = isSplit
      for (otherEntry in idToEntry.values) {
        otherEntry.applyWindowInfo((layoutState.getInfo(otherEntry.id) ?: continue).copy())
      }
    }
    getToolWindowPane(info.safeToolWindowPaneId).buttonManager.getStripeFor(
      anchor = entry.readOnlyWindowInfo.anchor,
      isSplit = entry.readOnlyWindowInfo.isSplit,
    ).revalidate()
  }

  fun setContentUiType(id: String, type: ToolWindowContentUiType) {
    val info = getRegisteredMutableInfoOrLogError(id)
    info.contentUiType = type
    val entry = idToEntry.get(id)!!
    entry.applyWindowInfo(info.copy())
    fireStateChanged(ToolWindowManagerEventType.SetContentUiType, entry.toolWindow)
  }

  internal fun setSideToolAndAnchor(id: String, paneId: String, anchor: ToolWindowAnchor, order: Int, isSplit: Boolean) {
    if (forwardToFrameOwner(id) { setSideToolAndAnchor(id, paneId, anchor, order, isSplit) }) {
      return
    }
    val entry = idToEntry.get(id)!!
    val readOnlyInfo = entry.readOnlyWindowInfo
    if (paneId == readOnlyInfo.safeToolWindowPaneId && anchor == readOnlyInfo.anchor &&
        order == readOnlyInfo.order && readOnlyInfo.isSplit == isSplit) {
      return
    }

    val info = getRegisteredMutableInfoOrLogError(id)
    hideIfNeededAndShowAfterTask(entry, info) {
      info.isSplit = isSplit
      doSetAnchor(
        entry = entry,
        info = info,
        paneId = paneId,
        anchor = anchor,
        order = order,
        currentInfo = null,
        layoutState = layoutState,
      )
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
      executeHide(entry = entry, info = info, dirtyMode = true)
    }

    task()

    if (wasVisible) {
      ToolWindowCollector.getInstance().recordShown(project, source, info)
      info.isVisible = true
      val infoSnapshot = info.copy()
      entry.applyWindowInfo(infoSnapshot)
      doShowWindow(entry = entry, info = infoSnapshot, dirtyMode = true)
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

    if (forwardToFrameOwner(id) { setToolWindowAutoHide(id, autoHide) }) {
      return
    }

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

    if (forwardToFrameOwner(id) { setToolWindowType(id, type) }) {
      return
    }

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
    decorators.updateStateAndRemoveDecorator(info, entry, dirtyMode)
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
    if (toolWindow.isPresented) {
      // the layout of the frame owner has the default state
      return
    }
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
    if (forwardToFrameOwner(toolWindow) { stretchWidth(it, value) }) {
      return
    }
    getToolWindowPane(toolWindow).stretchWidth(toolWindow, value)
  }

  override fun isMaximized(window: ToolWindow): Boolean {
    var isPresentingMaximized = false
    if (forwardToFrameOwner(window) { isPresentingMaximized = isMaximized(it) }) {
      return isPresentingMaximized
    }
    return getToolWindowPane(window).isMaximized(window)
  }

  override fun setMaximized(window: ToolWindow, maximized: Boolean) {
    if (forwardToFrameOwner(window) { setMaximized(it, maximized) }) {
      return
    }
    if (window.type == ToolWindowType.FLOATING && window is ToolWindowImpl) {
      idToEntry.get(window.id)?.floatingDecorator?.toggleMaximized()
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
    if (forwardToFrameOwner(toolWindow) { stretchHeight(it, value) }) {
      return
    }
    getToolWindowPane(toolWindow).stretchHeight(toolWindow, value)
  }

  internal fun toolWindowAvailable(toolWindow: ToolWindowImpl) {
    if (toolWindow.isPresented) {
      frameOwnerPresentation?.availabilityChanged(toolWindow)
      fireStateChanged(ToolWindowManagerEventType.ToolWindowAvailable, toolWindow)
      return
    }

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
    if (toolWindow.isPresented) {
      frameOwnerPresentation?.availabilityChanged(toolWindow)
      fireStateChanged(ToolWindowManagerEventType.ToolWindowUnavailable, toolWindow)
      return
    }

    val entry = idToEntry.get(toolWindow.id)!!
    val moveFocusAfter = toolWindow.isActive && toolWindow.isVisible
    val info = getRegisteredMutableInfoOrLogError(toolWindow.id)
    executeHide(entry = entry, info = info, dirtyMode = false, mutation = {
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
    if (source.toolWindow.isPresented) {
      // the decorator of the presenting tool window gets the same event, and the frame owner keeps the size
      return
    }
    decorators.movedOrResized(source)
  }

  internal fun movedOrResizedStateChanged(toolWindow: ToolWindow) {
    fireStateChanged(ToolWindowManagerEventType.MovedOrResized, toolWindow)
  }

  /**
   * Focuses a tool window when the editor area has no editor to take the focus.
   * The project open, the close of the last editor, and an empty state that cannot keep its focus claim use this rule.
   * See [findDefaultToolWindowToFocus] for the guards and the target.
   *
   * @param emptyStateClaimsFocus true if the empty state of the editor area takes the focus.
   * Each caller reads the claim itself, because the callers see the claim at different times.
   */
  @RequiresEdt
  internal fun focusDefaultToolWindow(emptyStateClaimsFocus: Boolean) {
    if (project.isDisposed || ApplicationManager.getApplication().isDisposed) {
      return
    }
    val entry = findDefaultToolWindowToFocus(emptyStateClaimsFocus) ?: return
    activateToolWindow(entry, getRegisteredMutableInfoOrLogError(entry.id))
  }

  /**
   * Returns the tool window that [focusDefaultToolWindow] focuses, or null if it focuses nothing.
   * It returns null when the empty state claims the focus, or when a tool window is active.
   *
   * Otherwise, it returns the first visible tool window in this order: the active stack, the persistent active stack,
   * and the recent tool windows.
   * If none of them is visible, it returns the first available visible docked tool window in the layout order.
   * The layout order sorts by the anchor (left, bottom, right, top), and then by the order on that anchor.
   */
  @VisibleForTesting
  @RequiresEdt
  internal fun findDefaultToolWindowToFocus(emptyStateClaimsFocus: Boolean): ToolWindowEntry? {
    if (emptyStateClaimsFocus || activeToolWindowId != null) {
      return null
    }
    return activeStack.stack.firstOrNull { it.readOnlyWindowInfo.isVisible }
           ?: activeStack.persistentStack.firstOrNull { it.readOnlyWindowInfo.isVisible }
           ?: recentToolWindowsState.firstNotNullOfOrNull { id -> idToEntry.get(id)?.takeIf { it.readOnlyWindowInfo.isVisible } }
           ?: idToEntry.values
             .filter { it.toolWindow.isAvailable && it.readOnlyWindowInfo.isVisible && it.readOnlyWindowInfo.isDocked }
             .minWithOrNull(compareBy({ getLayoutAnchorRank(it.readOnlyWindowInfo.anchor) }, { it.readOnlyWindowInfo.order }))
  }

  internal fun setShowStripeButton(id: String, value: Boolean) {
    if (isNewUi) {
      LOG.info("Ignore setShowStripeButton(id=$id, value=$value) - not applicable for a new UI")
      return
    }

    doSetShowStripeButton(id, value)
  }

  /**
   * Puts the stripe button back in both UIs; the inverse of [hideToolWindow] with `removeFromStripe = true`.
   * It neither shows nor activates the window.
   */
  @ApiStatus.Internal
  fun restoreStripeButton(id: String) {
    doSetShowStripeButton(id, true)
    // the frame owner has the stripe button of a presented tool window
    forwardToFrameOwner(id) { presenting -> if (presenting.isAvailable) setStripeButtonShown(id, true) }
  }

  private fun doSetShowStripeButton(id: String, value: Boolean) {
    idToEntry.get(id)?.toolWindow?.takeIf { it.isPresented }?.let {
      // the frame owner keeps the stripe button, and the tool window comes to the stripe of its project or leaves it
      setOnProjectStripe(it, value)
      return
    }
    val entry = idToEntry.get(id) ?: throw IllegalStateException("window with id=\"$id\" isn't registered")
    if (value) {
      presentation.ownerComesToStripe(entry.toolWindow)
    }
    setStripeButtonShown(id, value)
  }

  /**
   * Shows or removes the stripe button of the tool window [id] of this manager. [ToolWindowManagerPresentation] calls it for
   * a presenting tool window, whose stripe button is also for its frame guests.
   */
  internal fun setStripeButtonShown(id: String, value: Boolean) {
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
}
