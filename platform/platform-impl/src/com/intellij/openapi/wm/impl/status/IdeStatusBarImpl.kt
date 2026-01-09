// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.accessibility.AccessibilityUtils
import com.intellij.codeWithMe.ClientId
import com.intellij.ide.HelpTooltipManager
import com.intellij.ide.IdeEventQueue
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger.StatusBarPopupShown
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger.StatusBarWidgetClicked
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.application.impl.islands.isIjpl217440
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.LoadingOrder.Orderable
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.progress.ProgressModel
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.progress.impl.BridgeTaskSupport
import com.intellij.openapi.progress.impl.PerProjectTaskInfoEntityCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.BalloonHandler
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.StatusBarWidget.*
import com.intellij.openapi.wm.WidgetPresentation
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.openapi.wm.impl.status.TextPanel.WithIconAndArrows
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsActionGroup
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.openapi.wm.impl.status.widget.WidgetPresentationWrapper
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneProjectListener
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.progress.ProgressState
import com.intellij.platform.util.progress.StepState
import com.intellij.ui.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.border.name
import com.intellij.ui.popup.AbstractPopup
import com.intellij.ui.popup.NotificationPopup
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.util.height
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.MouseEvent
import java.util.function.Supplier
import javax.accessibility.Accessible
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.event.HyperlinkListener
import kotlin.math.max

private const val UI_CLASS_ID = "IdeStatusBarUI"
private val WIDGET_ID = Key.create<String>("STATUS_BAR_WIDGET_ID")

private val LOG = logger<IdeStatusBarImpl>()

private val minIconHeight: Int
  get() = JBUIScale.scale(18 + 1 + 1)

/**
 * Interface for widgets that can be recreated for child status bars (detached windows).
 * Preferred over [StatusBarWidget.Multiframe] as it provides full context for recreation.
 */
internal interface ChildStatusBarWidget {
  fun createForChild(childStatusBar: IdeStatusBarImpl): StatusBarWidget
}

@OptIn(ExperimentalCoroutinesApi::class)
open class IdeStatusBarImpl @Internal constructor(
  parentCs: CoroutineScope,
  private val getProject: () -> Project?,
  addToolWindowWidget: Boolean,
  currentFileEditorFlow: StateFlow<FileEditor?>? = null,
) : JComponent(), Accessible, StatusBarEx, UiDataProvider {
  private val customCurrentFileEditorFlow: MutableStateFlow<StateFlow<FileEditor?>?> = MutableStateFlow(currentFileEditorFlow)

  internal val coroutineScope = parentCs.childScope("IdeStatusBarImpl", supervisor = false)
  private var infoAndProgressPanel: InfoAndProgressPanel? = null

  internal enum class WidgetEffect {
    HOVER,
    PRESSED
  }

  private val widgetRegistry = WidgetRegistry()
  private var leftPanel: JPanel? = null

  private var rightPanelLayout = GridBagLayout()
  private val rightPanel: JPanel

  private val centerPanel: JPanel
  private val effectRenderer = WidgetEffectRenderer(this)
  private var info: @NlsContexts.StatusBarText String? = null

  private var preferredTextHeight: Int = 0

  private val childManager = ChildStatusBarManager(this, widgetRegistry)
  private val listeners = EventDispatcher.create(StatusBarListener::class.java)

  private val progressFlow = MutableSharedFlow<ProgressSetChangeEvent>(replay = 1, extraBufferCapacity = Int.MAX_VALUE)

  // todo remove with isIjpl217440 property
  internal var borderPainter: BorderPainter = DefaultBorderPainter()

  companion object {
    internal val HOVERED_WIDGET_ID: DataKey<String> = DataKey.create("HOVERED_WIDGET_ID")

    const val NAVBAR_WIDGET_KEY: String = "NavBar"
  }

  override fun findChild(c: Component): StatusBar {
    var parent: Component? = c
    while (parent != null) {
      if (parent is IdeFrame) {
        return parent.statusBar!!
      }
      parent = parent.parent
    }
    return this
  }

  //=== CREATE CHILD: New child gets all existing widgets ===
  @RequiresEdt
  @Internal
  override fun createChild(coroutineScope: CoroutineScope, frame: IdeFrame, currentFileEditorFlow: StateFlow<FileEditor?>): StatusBar {
    return childManager.createChild(coroutineScope, currentFileEditorFlow)
  }

  override val component: JComponent?
    get() = this

  private val disposable = Disposer.newDisposable()

  init {
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      Disposer.dispose(disposable)
    }

    layout = BorderLayout()
    isOpaque = true
    border = (if (ExperimentalUI.isNewUI()) {
      CompoundBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.StatusBar.BORDER_COLOR, 1, 0, 0, 0), JBUI.Borders.empty(0, 10))
    }
    else {
      JBUI.Borders.empty(1, 0, 0, 6)
    })

    centerPanel = StatusBarPanel(BorderLayout())
    centerPanel.isOpaque = false
    centerPanel.border = if (ExperimentalUI.isNewUI()) JBUI.Borders.empty() else JBUI.Borders.empty(0, 1)
    add(centerPanel, BorderLayout.CENTER)

    rightPanel = StatusBarPanel(rightPanelLayout)
    rightPanel.isOpaque = false
    rightPanel.border = JBUI.Borders.emptyLeft(1)
    add(rightPanel, BorderLayout.EAST)

    if (addToolWindowWidget) {
      val disposable = Disposer.newDisposable()
      coroutineScope.coroutineContext.job.invokeOnCompletion { Disposer.dispose(disposable) }

      val toolWindowWidget = ToolWindowsWidget(disposable, this)
      val toolWindowWidgetComponent = wrapCustomStatusBarWidget(toolWindowWidget)
      widgetRegistry.put(toolWindowWidget.ID(), WidgetBean(widget = toolWindowWidget,
                                                           position = Position.LEFT,
                                                           component = toolWindowWidgetComponent,
                                                           order = LoadingOrder.ANY))
      Disposer.register(disposable, toolWindowWidget)
      toolWindowWidgetComponent.border = if (SystemInfoRt.isMac) JBUI.Borders.empty(2, 0, 2, 4) else JBUI.Borders.empty()
      leftPanel().add(toolWindowWidgetComponent)
    }

    updateUI()

    enableEvents(AWTEvent.MOUSE_EVENT_MASK)
    enableEvents(AWTEvent.MOUSE_MOTION_EVENT_MASK)
    IdeEventQueue.getInstance().addDispatcher(object : IdeEventQueue.NonLockedEventDispatcher {
      override fun dispatch(e: AWTEvent): Boolean {
        return if (e is MouseEvent) {
          dispatchMouseEvent(e)
        }
        else {
          false
        }
      }
    }, coroutineScope)
  }

  internal fun initialize() {
    LOG.info("Initializing status bar")

    registerCloneTasks()
    project?.service<PerProjectTaskInfoEntityCollector>()?.startCollectingActiveTasks()
  }

  private fun createInfoAndProgressPanel(): InfoAndProgressPanel {
    infoAndProgressPanel?.let {
      return it
    }

    val infoAndProgressPanel = InfoAndProgressPanel(
      statusBar = this,
      coroutineScope = coroutineScope.childScope("InfoAndProgressPanel of $this"),
    )
    centerPanel.add(infoAndProgressPanel.component)
    this.infoAndProgressPanel = infoAndProgressPanel
    return infoAndProgressPanel
  }

  override fun getPreferredSize(): Dimension {
    val size = super.getPreferredSize()!!
    val insets = insets
    val minHeight = insets.top + insets.bottom + max(minIconHeight, preferredTextHeight)
    return Dimension(size.width, size.height.coerceAtLeast(minHeight))
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[CommonDataKeys.PROJECT] = project
    sink[PlatformDataKeys.STATUS_BAR] = this
    sink[HOVERED_WIDGET_ID] = effectRenderer.getEffectWidgetId()
  }

  override fun setVisible(aFlag: Boolean) {
    super.setVisible(aFlag)
    childManager.setVisibilityForAll(aFlag)
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun addWidget(widget: StatusBarWidget) {
    addWidget(widget, Position.RIGHT, LoadingOrder.ANY)
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun addWidget(widget: StatusBarWidget, anchor: String) {
    val order = LoadingOrder.anchorToOrder(anchor)
    EdtInvocationManager.invokeLaterIfNeeded { addWidget(widget, Position.RIGHT, order) }
  }

  override fun addWidget(widget: StatusBarWidget, parentDisposable: Disposable) {
    EdtInvocationManager.invokeLaterIfNeeded { addWidget(widget, Position.RIGHT, LoadingOrder.ANY) }
    val id = widget.ID()
    Disposer.register(parentDisposable) { removeWidget(id) }
  }

  override fun addWidget(widget: StatusBarWidget, anchor: String, parentDisposable: Disposable) {
    val order = LoadingOrder.anchorToOrder(anchor)
    EdtInvocationManager.invokeLaterIfNeeded {
      addWidget(widget = widget, position = Position.RIGHT, anchor = order)
      val id = widget.ID()
      Disposer.register(parentDisposable) { removeWidget(id) }
    }
  }

  @ApiStatus.Experimental
  @RequiresEdt
  fun setCentralWidget(id: String, component: JComponent?) {
    if (component == null) {
      widgetRegistry.remove(id)
    }
    else {
      val widget = object : StatusBarWidget {
        override fun ID(): String = id
      }
      widgetRegistry.put(id, WidgetBean(widget = widget, position = Position.CENTER, component = component, order = LoadingOrder.ANY))
    }

    val infoAndProgressPanel = createInfoAndProgressPanel()
    infoAndProgressPanel.setCentralComponent(component)
    infoAndProgressPanel.component.revalidate()
  }

  /**
   * Adds widget to the left side of the status bar.
   * Please note there is no hover effect when the mouse is over the widget.
   * Use [addWidget] to add widget to the right side of the status bar, in this case the hover effect is on.
   * @param widget widget to add
   */
  internal suspend fun addWidgetToLeft(widget: StatusBarWidget) {
    withContext(Dispatchers.UiWithModelAccess) {
      addWidget(widget, Position.LEFT, LoadingOrder.ANY)
    }
  }

  @RequiresEdt
  @Internal
  fun addWidget(widget: StatusBarWidget, anchor: LoadingOrder) {
    addWidget(widget, Position.RIGHT, anchor)
  }

  internal suspend fun init(project: Project, frame: IdeFrame, extraItems: List<Pair<StatusBarWidget, LoadingOrder>> = emptyList()) {
    val service = project.serviceAsync<StatusBarWidgetsManager>()
    val items = span("status bar pre-init") {
      service.init(frame)
    }
    span("status bar init") {
      doInit(widgets = items + extraItems, parentDisposable = service)
    }
  }

  //=== BATCH INIT: Optimized for performance, still propagates ===
  private suspend fun doInit(widgets: List<Pair<StatusBarWidget, LoadingOrder>>, parentDisposable: Disposable) {
    val anyModality = ModalityState.any().asContextElement()

    // Create components in parallel (performance optimization)
    val beans: List<WidgetBean> = span("status bar widget creating") {
      widgets.map { (widget, anchor) ->
        val component = span(widget.ID(), Dispatchers.UiWithModelAccess + anyModality) {
          val c = wrap(widget)
          if (c is StatusBarWidgetWrapper) {
            c.beforeUpdate()
          }
          c
        }
        WidgetBean(widget, Position.RIGHT, component, anchor)
      }
    }

    withContext(Dispatchers.UiWithModelAccess + anyModality + CoroutineName("status bar widget adding")) {
      // Add all to self
      for (bean in beans) {
        addWidgetToSelf(bean, parentDisposable)
      }
      sortRightWidgets()

      // Propagate all to existing children
      if (!childManager.isEmpty()) {
        for (bean in beans) {
          childManager.propagateToAll(bean.widget, bean.position, bean.order)
        }
      }
    }

    // Fire events
    if (listeners.hasListeners()) {
      withContext(Dispatchers.UiWithModelAccess + anyModality) {
        for (bean in beans) {
          fireWidgetAdded(bean.widget, bean.anchor)
        }
      }
    }

    withContext(Dispatchers.UiWithModelAccess) {
      PopupHandler.installPopupMenu(this@IdeStatusBarImpl, StatusBarWidgetsActionGroup.GROUP_ID, ActionPlaces.STATUS_BAR_PLACE)
    }
  }

  private fun sortRightWidgets() {
    val sorted = widgetRegistry.filterByPosition(Position.RIGHT)

    // inject not available extension to make sort correct —
    // e.g. `after PowerSaveMode` must work even if `PowerSaveMode` widget is disabled,
    // because `PowerSaveMode` can specify something like `after Encoding`
    StatusBarWidgetFactory.EP_NAME.filterableLazySequence()
      .filter { it.id != null && !widgetRegistry.containsKey(it.id!!) }
      .mapTo(sorted) {
        object : Orderable {
          override val orderId: String?
            get() = it.id
          override val order: LoadingOrder
            get() = it.order
        }
      }

    LoadingOrder.sortByLoadingOrder(sorted)
    for ((index, item) in sorted.withIndex()) {
      rightPanelLayout.setConstraints((item as? WidgetBean ?: continue).component, GridBagConstraints().apply {
        gridx = index
        gridy = 0
        fill = GridBagConstraints.VERTICAL
        weighty = 1.0
        weightx = 1.0
        anchor = GridBagConstraints.PAGE_END
      })
    }
  }

  //=== CORE: Add widget to THIS status bar only (no propagation) ===
  @RequiresEdt
  internal fun addWidgetToSelf(bean: WidgetBean, parentDisposable: Disposable? = null) {
    LOG.debug { "addWidgetToSelf: ${bean.widget.ID()}" }

    widgetRegistry.put(bean.widget.ID(), bean)

    val effectiveDisposable = parentDisposable ?: disposable
    Disposer.register(effectiveDisposable, bean.widget)
    bean.widget.install(this)

    val panel = getTargetPanel(bean.position)
    if (bean.position == Position.LEFT && panel.componentCount == 0) {
      bean.component.border = if (SystemInfoRt.isMac) JBUI.Borders.empty(2, 0, 2, 4) else JBUI.Borders.empty()
    }
    panel.add(bean.component)
    panel.revalidate()
  }

  //=== PUBLIC API: Add widget + propagate to all children ===
  @RequiresEdt
  private fun addWidget(widget: StatusBarWidget, position: Position, anchor: LoadingOrder) {
    val component = wrap(widget)
    if (component is StatusBarWidgetWrapper) {
      component.beforeUpdate()
    }

    addWidgetToSelf(WidgetBean(widget, position, component, anchor))

    if (position == Position.RIGHT) {
      sortRightWidgets()
    }

    // Propagate to all existing children
    childManager.propagateToAll(widget, position, anchor)

    fireWidgetAdded(widget = widget, anchor = anchor.toString())
  }

  private fun getTargetPanel(position: Position): JPanel {
    return when (position) {
      Position.RIGHT -> rightPanel
      Position.LEFT -> leftPanel()
      else -> centerPanel
    }
  }

  private fun leftPanel(): JPanel {
    var panel = leftPanel
    if (panel == null) {
      panel = JPanel()
      panel.border = JBUI.Borders.empty(0, 4, 0, 1)
      panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
      panel.isOpaque = false
      add(panel, BorderLayout.WEST)
      leftPanel = panel
    }
    return panel
  }

  override fun setInfo(s: String?) {
    setInfo(s, null)
  }

  override fun setInfo(s: @Nls String?, requestor: String?) {
    EdtInvocationManager.invokeLaterIfNeeded {
      info = createInfoAndProgressPanel().setText(s, requestor)
    }
  }

  override fun getInfo(): @NlsContexts.StatusBarText String? = info

  @Suppress("UsagesOfObsoleteApi")
  override fun addProgress(indicator: ProgressIndicatorEx, info: TaskInfo) {
    @Suppress("DEPRECATION")
    BridgeTaskSupport.getInstance().withBridgeBackgroundProgress(project, indicator, info)
  }

  internal fun addProgressImpl(progressModel: ProgressModel, info: TaskInfo) {
    check(progressFlow.tryEmit(ProgressSetChangeEvent(
      newProgress = Triple(info, progressModel, ClientId.currentOrNull),
      existingProgresses = infoAndProgressPanel?.backgroundProcesses ?: emptyList(),
    )))
    createInfoAndProgressPanel().addProgress(progressModel, info)
  }

  internal fun notifyProgressRemoved(backgroundProcesses: List<Pair<TaskInfo, ProgressModel>>) {
    check(progressFlow.tryEmit(ProgressSetChangeEvent(newProgress = null, existingProgresses = backgroundProcesses)))
  }

  /**
   * Reports currently displayed progresses and the ones that will be displayed in future (never ending flow).
   *
   * NOTE: correct client id value is reported only for newly appearing progresses, older ones are reported with local client id.
   * This isn't a problem for current usage when the subscription is performed on the first remote client's connection, but may need to be
   * corrected for potential future usages.
   */
  @Internal
  fun getVisibleProcessFlow(): Flow<VisibleProgress> {
    return flow {
      var firstTime = true
      progressFlow.collect { event ->
        if (firstTime) {
          firstTime = false
          event.existingVisibleProgresses.forEach { emit(it) }
        }
        event.newVisibleProgress?.let { emit(it) }
      }
    }
  }

  override fun getBackgroundProcessModels(): List<Pair<TaskInfo, ProgressModel>> {
    return (infoAndProgressPanel?.backgroundProcesses ?: emptyList())
  }

  override fun setProcessWindowOpen(open: Boolean) {
    createInfoAndProgressPanel().isProcessWindowOpen = open
  }

  override fun isProcessWindowOpen(): Boolean = infoAndProgressPanel?.isProcessWindowOpen ?: false

  override fun startRefreshIndication(tooltipText: @NlsContexts.Tooltip String?) {
    createInfoAndProgressPanel().setRefreshVisible(tooltipText)
    childManager.updateAll { it.startRefreshIndication(tooltipText) }
  }

  override fun stopRefreshIndication() {
    createInfoAndProgressPanel().setRefreshHidden()
    childManager.updateAll { it.stopRefreshIndication() }
  }

  override fun notifyProgressByBalloon(type: MessageType, htmlBody: @NlsContexts.PopupContent String): BalloonHandler {
    return notifyProgressByBalloon(type = type, htmlBody = htmlBody, icon = null, listener = null)
  }

  override fun notifyProgressByBalloon(
    type: MessageType,
    htmlBody: @NlsContexts.PopupContent String,
    icon: Icon?,
    listener: HyperlinkListener?,
  ): BalloonHandler {
    return createInfoAndProgressPanel().notifyByBalloon(type, htmlBody, icon, listener)
  }

  override fun fireNotificationPopup(content: JComponent, backgroundColor: Color?) {
    NotificationPopup(this, content, backgroundColor)
  }

  private fun applyWidgetEffect(component: JComponent?, widgetEffect: WidgetEffect?) {
    effectRenderer.applyEffect(component, widgetEffect)
  }

  override fun paintChildren(g: Graphics) {
    effectRenderer.paintBackground(g)
    super.paintChildren(g)
    if (!isIjpl217440) {
      borderPainter.paintAfterChildren(this, g)
    }
  }

  private fun dispatchMouseEvent(e: MouseEvent): Boolean {
    val rightPanel = rightPanel.takeIf { it.isVisible } ?: return false
    val component = e.component ?: return false
    if (ComponentUtil.getWindow(this) !== ComponentUtil.getWindow(component)) {
      applyWidgetEffect(null, null)
      return false
    }

    val point = SwingUtilities.convertPoint(component, e.point, rightPanel)
    val widget = getVisibleChildAt(rightPanel, point)
    val targetWidget = widget.takeIf { it !== rightPanel }
    if (e.clickCount == 0 || e.id == MouseEvent.MOUSE_RELEASED) {
      applyWidgetEffect(targetWidget, WidgetEffect.HOVER)
    }
    else if (e.clickCount == 1 && e.id == MouseEvent.MOUSE_PRESSED) {
      applyWidgetEffect(targetWidget, WidgetEffect.PRESSED)
    }

    if (e.isConsumed || widget == null) {
      return false
    }

    if (e.isPopupTrigger && (e.id == MouseEvent.MOUSE_PRESSED || e.id == MouseEvent.MOUSE_RELEASED)) {
      val project = project
      if (project != null) {
        val actionManager = ActionManager.getInstance()
        val group = actionManager.getAction(StatusBarWidgetsActionGroup.GROUP_ID) as? ActionGroup
        if (group != null) {
          val menu = actionManager.createActionPopupMenu(ActionPlaces.STATUS_BAR_PLACE, group)
          menu.setTargetComponent(this)
          menu.component.show(rightPanel, point.x, point.y)
          e.consume()
          return true
        }
      }
    }
    return false
  }

  /**
   * Unlike [Container.getComponentAt] will not return invisible child.
   * Unlike [Container.findComponentAt] or [SwingUtilities.getDeepestComponentAt] will not search deeper.
   */
  private fun getVisibleChildAt(component: JComponent, point: Point): JComponent? {
    if (component.isVisible && component.contains(point)) {
      return component.components.find { child ->
        child.isVisible && child.contains(point.x - child.x, point.y - child.y)
      } as? JComponent
    }
    return null
  }

  override fun getUIClassID(): String = UI_CLASS_ID

  override fun updateUI() {
    if (UIManager.get(UI_CLASS_ID) != null) {
      setUI(UIManager.getUI(this))
    }
    else {
      setUI(StatusBarUI())
    }
    GuiUtils.iterateChildren(this, { c ->
      if (c is JComponent) {
        val newBorder = when (c.border?.name) {
          JBUI.CurrentTheme.StatusBar.Widget.borderName() -> JBUI.CurrentTheme.StatusBar.Widget.border()
          JBUI.CurrentTheme.StatusBar.Widget.iconBorderName() -> JBUI.CurrentTheme.StatusBar.Widget.iconBorder()
          else -> null
        }
        if (newBorder != null) {
          c.border = newBorder
        }
      }
    })
    preferredTextHeight = TextPanel.computeTextHeight() + JBUI.CurrentTheme.StatusBar.Widget.border().getBorderInsets(null).height
  }

  override fun getComponentGraphics(g: Graphics): Graphics {
    return InternalUICustomization.runGlobalCGTransformWithInactiveFrameSupport(this, super.getComponentGraphics(g))
  }

  override fun removeWidget(id: String) {
    EdtInvocationManager.invokeLaterIfNeeded {
      val bean = widgetRegistry.remove(id)
      if (bean != null) {
        effectRenderer.clearIfMatches(bean.component)

        val targetPanel = getTargetPanel(bean.position)
        targetPanel.remove(bean.component)
        recreateLayoutIfIsEmptyRightPanel(targetPanel)
        targetPanel.revalidate()
        Disposer.dispose(bean.widget)
        fireWidgetRemoved(id)
      }
      childManager.updateAll { it.removeWidget(id) }
    }
  }

  private fun recreateLayoutIfIsEmptyRightPanel(panel: JPanel) {
    if (panel === rightPanel && panel.components.none { it.isVisible }) {
      // Workaround of a bug in AWT:
      // GridBagLayout.componentAdjusting is not set to NULL after removing the last visible child component.
      // That leads to a leak of the StatusBarWidget component, and that leads to a situation when the plugin can't be properly unloaded.
      rightPanelLayout = GridBagLayout()
      panel.layout = rightPanelLayout
      sortRightWidgets()
    }
  }

  override fun updateWidget(id: String) {
    EdtInvocationManager.invokeLaterIfNeeded {
      val widgetComponent = getWidgetComponent(id)
      if (widgetComponent != null) {
        if (widgetComponent is StatusBarWidgetWrapper) {
          widgetComponent.beforeUpdate()
        }
        widgetComponent.repaint()
        fireWidgetUpdated(id)
      }
      childManager.updateAll { it.updateWidget(id) }
    }
  }

  override fun getWidget(id: String): StatusBarWidget? = widgetRegistry.getWidget(id)

  override val allWidgets: Collection<StatusBarWidget>?
    get() = widgetRegistry.getAllWidgets()

  override fun getWidgetAnchor(id: String): String? = widgetRegistry.getAnchor(id)

  //todo: make private after removing all external usages
  @Internal
  fun getWidgetComponent(id: String): JComponent? = widgetRegistry.getComponent(id)

  override val project: Project?
    get() = getProject()

  @get:Internal
  override val currentEditor: StateFlow<FileEditor?> by lazy {
    customCurrentFileEditorFlow
      .flatMapLatest { customFlow ->
        customFlow
        ?: project?.serviceAsync<StatusBarWidgetsManager>()?.dataContext?.currentFileEditor
        ?: emptyFlow()
      }
      .stateIn(coroutineScope, SharingStarted.Eagerly, null)
  }

  @Internal
  fun setCurrentFileEditorFlow(flow: StateFlow<FileEditor?>?) {
    customCurrentFileEditorFlow.value = flow
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = AccessibleIdeStatusBarImpl()
      accessibleContext.accessibleName = UIBundle.message("status.bar.accessible.group.name")
    }
    return accessibleContext
  }

  override fun addListener(listener: StatusBarListener, parentDisposable: Disposable) {
    listeners.addListener(listener, parentDisposable)
  }

  private fun fireWidgetAdded(widget: StatusBarWidget, anchor: String?) {
    listeners.multicaster.widgetAdded(widget, anchor)
  }

  private fun fireWidgetUpdated(id: String) {
    listeners.multicaster.widgetUpdated(id)
  }

  private fun fireWidgetRemoved(id: String) {
    listeners.multicaster.widgetRemoved(id)
  }

  private fun registerCloneTasks() {
    CloneableProjectsService.getInstance()
      .collectCloneableProjects()
      .map { it.cloneableProject }
      .forEach { addProgress(indicator = it.progressIndicator, info = it.cloneTaskInfo) }
    ApplicationManager.getApplication().messageBus.connect(coroutineScope)
      .subscribe(CloneableProjectsService.TOPIC, object : CloneProjectListener {
        override fun onCloneAdded(progressIndicator: ProgressIndicatorEx, taskInfo: TaskInfo) {
          addProgress(progressIndicator, taskInfo)
        }
      })
  }

  @Suppress("RedundantInnerClassModifier")
  protected inner class AccessibleIdeStatusBarImpl : AccessibleJComponent() {
    override fun getAccessibleRole(): AccessibleRole = AccessibilityUtils.GROUPED_ELEMENTS
  }
}

@RequiresEdt
internal fun createComponentByWidgetPresentation(presentation: WidgetPresentation, project: Project, scope: CoroutineScope): JComponent {
  val toolTipTextSupplier = { runWithModalProgressBlocking(project, title = "") { presentation.getTooltipText() } }
  return when (presentation) {
    is TextWidgetPresentation -> {
      val panel = TextPanel(toolTipTextSupplier)
      panel.setTextAlignment(presentation.alignment)
      panel.border = JBUI.CurrentTheme.StatusBar.Widget.border()
      configurePresentationComponent(presentation, panel)

      scope.launch {
        presentation.text()
          .distinctUntilChanged()
          .collectLatest { text ->
            withContext(Dispatchers.EDT) {
              panel.isVisible = !text.isNullOrEmpty()
              panel.text = text
            }
          }
      }
      panel
    }
    is IconWidgetPresentation -> {
      val panel = WithIconAndArrows(toolTipTextSupplier)
      panel.border = JBUI.CurrentTheme.StatusBar.Widget.iconBorder()
      configurePresentationComponent(presentation, panel)

      scope.launch {
        presentation.icon()
          .distinctUntilChanged()
          .collectLatest { icon ->
            withContext(Dispatchers.EDT) {
              panel.icon = icon
              panel.isVisible = icon != null
              panel.repaint()
            }
          }
      }
      panel
    }
    else -> throw IllegalArgumentException("Unable to find a wrapper for presentation: ${presentation.javaClass.simpleName}")
  }
}

@RequiresEdt
private fun createComponentByWidgetPresentation(widget: StatusBarWidget): JComponent {
  val presentation = widget.getPresentation() ?: throw IllegalStateException("Widget $widget getPresentation() method must not return null")
  return when (presentation) {
    is IconPresentation -> IconPresentationComponent(presentation)
    is TextPresentation -> TextPresentationComponent(presentation)
    is MultipleTextValuesPresentation -> MultipleTextValues(presentation)
    else -> throw IllegalArgumentException("Unable to find a wrapper for presentation: ${presentation.javaClass.simpleName}")
  }
}

private fun configurePresentationComponent(presentation: WidgetPresentation, panel: JComponent) {
  presentation.getClickConsumer()?.let {
    StatusBarWidgetClickListener(it).installOn(panel, true)
  }
  ClientProperty.put(panel, HelpTooltipManager.SHORTCUT_PROPERTY, Supplier {
    runWithModalProgressBlocking(ModalTaskOwner.component(panel), title = "") { presentation.getShortcutText() }
  })
}

internal fun wrap(widget: StatusBarWidget): JComponent {
  val result = if (widget is CustomStatusBarWidget) {
    return wrapCustomStatusBarWidget(widget)
  }
  else {
    createComponentByWidgetPresentation(widget)
  }
  ToolTipManager.sharedInstance().registerComponent(result)
  ClientProperty.put(result, WIDGET_ID, widget.ID())
  result.putClientProperty(UIUtil.CENTER_TOOLTIP_DEFAULT, true)
  return result
}

private fun wrapCustomStatusBarWidget(widget: CustomStatusBarWidget): JComponent {
  val component = widget.component
  if (component.border == null) {
    component.border = if (widget is IconLikeCustomStatusBarWidget) {
      JBUI.CurrentTheme.StatusBar.Widget.iconBorder()
    }
    else {
      JBUI.CurrentTheme.StatusBar.Widget.border()
    }
  }

  // wrap with a panel, so it will fill the entire status bar height
  val result = if (component is JLabel) {
    val panel = JPanel(BorderLayout())
    panel.add(component, BorderLayout.CENTER)
    panel.isOpaque = false
    panel
  }
  else {
    component
  }
  return result
}

private class IconPresentationComponent(private val presentation: IconPresentation) : WithIconAndArrows(presentation::getTooltipText),
                                                                                      StatusBarWidgetWrapper {
  init {
    setTextAlignment(CENTER_ALIGNMENT)
    border = JBUI.CurrentTheme.StatusBar.Widget.iconBorder()
    presentation.getClickConsumer()?.let { clickConsumer ->
      StatusBarWidgetClickListener(clickConsumer = clickConsumer::consume).installOn(this, true)
    }
    ClientProperty.put(this, HelpTooltipManager.SHORTCUT_PROPERTY, Supplier(presentation::getShortcutText))
  }

  override fun beforeUpdate() {
    icon = presentation.getIcon()
    isVisible = hasIcon()
  }
}

private class TextPresentationComponent(
  private val presentation: TextPresentation,
) : TextPanel(presentation::getTooltipText), StatusBarWidgetWrapper {
  init {
    setTextAlignment(presentation.getAlignment())
    border = JBUI.CurrentTheme.StatusBar.Widget.border()
    presentation.getClickConsumer()?.let { clickConsumer ->
      StatusBarWidgetClickListener(clickConsumer::consume).installOn(this, true)
    }
    ClientProperty.put(this, HelpTooltipManager.SHORTCUT_PROPERTY, Supplier(presentation::getShortcutText))
  }

  override fun beforeUpdate() {
    text = presentation.getText()
    isVisible = text != null
  }
}

private class MultipleTextValues(private val presentation: MultipleTextValuesPresentation)
  : WithIconAndArrows(presentation::getTooltipText), StatusBarWidgetWrapper {
  init {
    isVisible = !presentation.getSelectedValue().isNullOrEmpty()
    setTextAlignment(CENTER_ALIGNMENT)
    border = JBUI.CurrentTheme.StatusBar.Widget.border()
    object : ClickListener() {
      override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
        val popup = presentation.getPopup() ?: return false
        StatusBarPopupShown.log(presentation::class.java)
        val dimension = getSizeFor(popup)
        val at = Point(0, -dimension.height)
        popup.show(RelativePoint(event.component, at))
        return true
      }

      private fun getSizeFor(popup: JBPopup): Dimension {
        return if (popup is AbstractPopup) popup.sizeForPositioning else popup.content.preferredSize
      }
    }.installOn(this, true)

    ClientProperty.put(this, HelpTooltipManager.SHORTCUT_PROPERTY, Supplier(presentation::getShortcutText))
  }

  override fun beforeUpdate() {
    val value = presentation.getSelectedValue()
    text = value
    icon = presentation.getIcon()
    isVisible = !Strings.isEmpty(value)
  }
}

private class StatusBarWidgetClickListener(private val clickConsumer: (MouseEvent) -> Unit) : ClickListener() {
  override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
    if (!e.isPopupTrigger && MouseEvent.BUTTON1 == e.button) {
      StatusBarWidgetClicked.log(clickConsumer.javaClass)
      WriteIntentReadAction.run {
        clickConsumer(e)
      }
    }
    return true
  }
}

internal interface StatusBarWidgetWrapper {
  fun beforeUpdate()
}

internal fun adaptV2Widget(
  id: String,
  dataContext: WidgetPresentationDataContext,
  parentCoroutineScope: CoroutineScope,
  presentationFactory: (WidgetPresentationDataContext, CoroutineScope) -> WidgetPresentation,
): StatusBarWidget {
  val factory = object : WidgetPresentationFactory {
    override fun createPresentation(context: WidgetPresentationDataContext, scope: CoroutineScope) = presentationFactory(context, scope)
  }
  return WidgetPresentationWrapper(id = id, factory = factory, dataContext = dataContext, scope = parentCoroutineScope.childScope(id))
}

private class StatusBarPanel(layout: LayoutManager) : JPanel(layout) {
  init {
    updateFont()
  }

  override fun updateUI() {
    super.updateUI()
    updateFont()
  }

  private fun updateFont() {
    font = JBUI.CurrentTheme.StatusBar.font()
  }
}

@Internal
class VisibleProgress(
  val title: @NlsContexts.ProgressTitle String,
  val clientId: ClientId?,
  val canceler: (() -> Unit)?,
  val state: Flow<ProgressState>, /* finite */
) {
  override fun toString(): String {
    return "VisibleProgress['$title', ${if (canceler == null) "non-" else ""}cancelable]"
  }
}

private val EMPTY_PROGRESS = StepState(-1.0, null, null)

private fun createVisibleProgress(progressModel: ProgressModel, info: TaskInfo, clientId: ClientId?): VisibleProgress {
  val stateFlow = MutableStateFlow(EMPTY_PROGRESS)
  val activeFlow = MutableStateFlow(true)
  val updater = {
    stateFlow.value = StepState(
      fraction = if (progressModel.isIndeterminate()) -1.0 else progressModel.getFraction(),
      text = progressModel.getText(),
      details = progressModel.getDetails(),
    )
  }

  val finisher = { activeFlow.value = false }

  progressModel.addOnChangeAction {
    updater()
  }
  progressModel.addOnFinishAction { taskInfo ->
    if (taskInfo == info) {
      finisher()
    }
  }

  if (progressModel.isFinished(info)) {
    finisher()
  }
  else {
    updater()
  }

  val state = stateFlow.combine(activeFlow) { state, active -> state.takeIf { active } }
    .takeWhile { it != null }.map { it!! }

  return VisibleProgress(
    title = info.title,
    clientId = clientId,
    canceler = { progressModel.cancel() }.takeIf { info.isCancellable },
    state = state,
  )
}

private class ProgressSetChangeEvent(
  private val newProgress: Triple<TaskInfo, ProgressModel, ClientId?>?,
  private val existingProgresses: List<Pair<TaskInfo, ProgressModel>>,
) {
  val newVisibleProgress by lazy {
    newProgress?.let { createVisibleProgress(it.second, it.first, it.third) }
  }
  val existingVisibleProgresses by lazy {
    existingProgresses.map { createVisibleProgress(it.second, it.first, null) }
  }
}
