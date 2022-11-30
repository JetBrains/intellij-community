// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "OVERRIDE_DEPRECATION")

package com.intellij.openapi.wm.impl.status

import com.intellij.diagnostic.IdeMessagePanel
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.UISettings.Companion.shadowInstance
import com.intellij.ide.ui.UISettingsListener
import com.intellij.notification.impl.widget.IdeNotificationArea
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.BalloonHandler
import com.intellij.openapi.util.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.StatusBarWidget.Multiframe
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetWrapper
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsActionGroup
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneProjectListener
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneableProject
import com.intellij.openapi.wm.impl.welcomeScreen.recentProjects.CloneableProjectItem
import com.intellij.ui.ClientProperty
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.popup.NotificationPopup
import com.intellij.util.ArrayUtil
import com.intellij.util.EventDispatcher
import com.intellij.util.ObjectUtils
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.event.MouseEvent
import java.util.*
import java.util.function.Consumer
import java.util.function.Supplier
import javax.accessibility.Accessible
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.*
import javax.swing.border.Border
import javax.swing.event.HyperlinkListener

private const val uiClassId = "IdeStatusBarUI"
private val WIDGET_ID = Key.create<String>("STATUS_BAR_WIDGET_ID")
private val MIN_ICON_HEIGHT = JBUI.scale(18 + 1 + 1)
private val LOG = logger<IdeStatusBarImpl>()

open class IdeStatusBarImpl @ApiStatus.Internal constructor(
  override final val frame: IdeFrame,
  addToolWindowsWidget: Boolean,
) : JComponent(), Accessible, StatusBarEx, IdeEventQueue.EventDispatcher, DataProvider {
  private val infoAndProgressPanel: InfoAndProgressPanel?

  private enum class Position {
    LEFT,
    RIGHT,
    CENTER
  }

  enum class WidgetEffect {
    HOVER,
    PRESSED
  }

  private val widgetMap: MutableMap<String, WidgetBean> = LinkedHashMap()
  private var leftPanel: JPanel? = null
  private var rightPanel: JPanel? = null
  private var centerPanel: JPanel? = null
  private var effectComponent: JComponent? = null
  private var info: @NlsContexts.StatusBarText String? = null
  private var editorProvider: Supplier<FileEditor?>? = null
  private val customComponentIds: MutableList<String> = ArrayList()
  private val children: MutableSet<IdeStatusBarImpl> = HashSet()
  private val listeners = EventDispatcher.create(StatusBarListener::class.java)

  companion object {
    @JvmField
    val HOVERED_WIDGET_ID = DataKey.create<String>("HOVERED_WIDGET_ID")
    @JvmField
    val WIDGET_EFFECT_KEY = Key.create<WidgetEffect>("TextPanel.widgetEffect")
    const val NAVBAR_WIDGET_KEY = "NavBar"

    private fun wrap(widget: StatusBarWidget): JComponent {
      if (widget is CustomStatusBarWidget) {
        val component = widget.component
        if (component.border == null) {
          component.border = if (widget is IconLikeCustomStatusBarWidget) JBUI.CurrentTheme.StatusBar.Widget.iconBorder() else JBUI.CurrentTheme.StatusBar.Widget.border()
        }
        // wrap with a panel, so it will fill the entire status bar height
        val result = if (component is JLabel) NonOpaquePanel(BorderLayout(), component) else component
        ClientProperty.put(result, WIDGET_ID, widget.ID())
        return result
      }
      val presentation = widget.presentation
      if (presentation == null) {
        LOG.error("Widget $widget getPresentation() method must not return null")
      }
      val wrapper = StatusBarWidgetWrapper.wrap(presentation!!)
      ClientProperty.put(wrapper, WIDGET_ID, widget.ID())
      wrapper.putClientProperty(UIUtil.CENTER_TOOLTIP_DEFAULT, java.lang.Boolean.TRUE)
      return wrapper
    }
  }

  private class WidgetBean {
    var component: JComponent? = null
    var position: Position? = null
    var widget: StatusBarWidget? = null
    var anchor: String? = null

    companion object {
      fun create(widget: StatusBarWidget,
                 position: Position,
                 component: JComponent,
                 anchor: String): WidgetBean {
        val bean = WidgetBean()
        bean.widget = widget
        bean.position = position
        bean.component = component
        bean.anchor = anchor
        return bean
      }
    }
  }

  override fun findChild(c: Component): StatusBar {
    var eachParent: Component? = c
    var frame: IdeFrame? = null
    while (eachParent != null) {
      if (eachParent is IdeFrame) {
        frame = eachParent
      }
      eachParent = eachParent.parent
    }
    return if (frame != null) frame.statusBar!! else this
  }

  private fun updateChildren(consumer: Consumer<in IdeStatusBarImpl>) {
    for (child in children) {
      consumer.accept(child)
    }
  }

  override fun createChild(frame: IdeFrame): StatusBar {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val bar = IdeStatusBarImpl(frame, false)
    bar.isVisible = isVisible
    children.add(bar)
    Disposer.register(this, bar)
    Disposer.register(bar) { children.remove(bar) }
    for (eachBean in widgetMap.values) {
      if (eachBean.widget is Multiframe) {
        val copy = (eachBean.widget as Multiframe?)!!.copy()
        bar.addWidget(copy, eachBean.position!!, eachBean.anchor!!)
      }
    }
    bar.repaint()
    return bar
  }

  override val component: JComponent?
    get() = this

  init {
    layout = BorderLayout()
    border = (if (ExperimentalUI.isNewUI()) {
      JBUI.Borders.compound(
        JBUI.Borders.customLine(JBUI.CurrentTheme.StatusBar.BORDER_COLOR, 1, 0, 0, 0),
        JBUI.Borders.empty(0, 10)
      )!!
    }
    else {
      JBUI.Borders.empty(1, 0, 0, 6)
    })
    infoAndProgressPanel = InfoAndProgressPanel(shadowInstance)
    Disposer.register(this, infoAndProgressPanel)
    addWidget(infoAndProgressPanel, Position.CENTER, "__IGNORED__")
    val project = frame.project
    project?.messageBus?.connect(this)?.subscribe(UISettingsListener.TOPIC, infoAndProgressPanel)
    registerCloneTasks()
    isOpaque = true
    @Suppress("LeakingThis")
    updateUI()
    if (addToolWindowsWidget) {
      addWidget(ToolWindowsWidget(this), Position.LEFT, "__IGNORED__")
    }
    enableEvents(AWTEvent.MOUSE_EVENT_MASK)
    enableEvents(AWTEvent.MOUSE_MOTION_EVENT_MASK)
    IdeEventQueue.getInstance().addDispatcher(this, this)
  }

  override fun getPreferredSize(): Dimension {
    val size = super.getPreferredSize()!!
    val insets = insets
    val minHeight = insets.top + insets.bottom + MIN_ICON_HEIGHT
    return Dimension(size.width, size.height.coerceAtLeast(minHeight))
  }

  override fun getData(dataId: String): Any? {
    return when {
      CommonDataKeys.PROJECT.`is`(dataId) -> project
      PlatformDataKeys.STATUS_BAR.`is`(dataId) -> this
      HOVERED_WIDGET_ID.`is`(dataId) -> ClientProperty.get(effectComponent, WIDGET_ID)
      else -> null
    }
  }

  override fun setVisible(aFlag: Boolean) {
    super.setVisible(aFlag)
    for (child in children) {
      child.isVisible = aFlag
    }
  }

  override fun setBorder(border: Border) {
    super.setBorder(border)
  }

  override fun addWidget(widget: StatusBarWidget) {
    @Suppress("DEPRECATION")
    addWidget(widget, "__AUTODETECT__")
  }

  override fun addWidget(widget: StatusBarWidget, anchor: String) {
    EdtInvocationManager.invokeLaterIfNeeded { addWidget(widget, Position.RIGHT, anchor) }
  }

  override fun addWidget(widget: StatusBarWidget, parentDisposable: Disposable) {
    @Suppress("DEPRECATION")
    addWidget(widget)
    val id = widget.ID()
    Disposer.register(parentDisposable) { removeWidget(id) }
  }

  override fun addWidget(widget: StatusBarWidget, anchor: String, parentDisposable: Disposable) {
    @Suppress("DEPRECATION")
    addWidget(widget, anchor)
    val id = widget.ID()
    Disposer.register(parentDisposable) { removeWidget(id) }
  }

  @ApiStatus.Experimental
  @RequiresEdt
  fun setCentralWidget(widget: StatusBarWidget, component: JComponent) {
    val panel: JPanel?
    infoAndProgressPanel!!.setCentralComponent(component)
    panel = infoAndProgressPanel
    doAddWidget(widget, Position.CENTER, "", component, panel)
  }

  /**
   * Adds widget to the left side of the status bar. Please note there is no hover effect when mouse is over the widget.
   * Use [.addWidget] to add widget to the right side of the status bar, in this case hover effect is on.
   * @param widget widget to add
   * @param parentDisposable when disposed, the widget will be removed from the status bar
   */
  fun addWidgetToLeft(widget: StatusBarWidget, parentDisposable: Disposable) {
    UIUtil.invokeLaterIfNeeded { addWidget(widget, Position.LEFT, "__IGNORED__") }
    val id = widget.ID()
    Disposer.register(parentDisposable) { removeWidget(id) }
  }

  override fun dispose() {
    removeCustomIndicationComponents()
    widgetMap.clear()
    children.clear()
    if (leftPanel != null) leftPanel!!.removeAll()
    if (rightPanel != null) rightPanel!!.removeAll()
    if (centerPanel != null) centerPanel!!.removeAll()
  }

  private fun removeCustomIndicationComponents() {
    for (id in customComponentIds) {
      removeWidget(id)
    }
    customComponentIds.clear()
  }

  @RequiresEdt
  @ApiStatus.Internal
  fun addRightWidget(widget: StatusBarWidget, anchor: String) {
    addWidget(widget, Position.RIGHT, anchor)
  }

  @RequiresEdt
  private fun addWidget(widget: StatusBarWidget, position: Position, anchor: String) {
    val c = wrap(widget)
    val panel = getTargetPanel(position)
    if (position == Position.LEFT && panel.componentCount == 0) {
      c.border = if (SystemInfoRt.isMac) JBUI.Borders.empty(2, 0, 2, 4) else JBUI.Borders.empty()
    }
    panel.add(c, getPositionIndex(position, anchor))
    if (c is StatusBarWidgetWrapper) {
      (c as StatusBarWidgetWrapper).beforeUpdate()
    }
    doAddWidget(widget, position, anchor, c, panel)
  }

  private fun doAddWidget(widget: StatusBarWidget, position: Position, anchor: String, c: JComponent, panel: JPanel?) {
    widgetMap[widget.ID()] = WidgetBean.create(widget, position, c, anchor)
    widget.install(this)
    panel!!.revalidate()
    Disposer.register(this, widget)
    fireWidgetAdded(widget, anchor)
    if (widget is Multiframe) {
      updateChildren { child: IdeStatusBarImpl -> child.addWidget(widget.copy(), position, anchor) }
    }
  }

  private fun getPositionIndex(position: Position, anchor: String): Int {
    if (Position.RIGHT == position && rightPanel!!.componentCount > 0) {
      var widgetAnchor: WidgetBean? = null
      var before = false
      val parts = StringUtil.split(anchor, " ")
      if (parts.size > 1) {
        widgetAnchor = widgetMap[parts[1]]
        before = "before".equals(parts[0], ignoreCase = true)
      }
      if (widgetAnchor == null) {
        widgetAnchor = widgetMap[IdeNotificationArea.WIDGET_ID]
        if (widgetAnchor == null) {
          widgetAnchor = widgetMap[IdeMessagePanel.FATAL_ERROR]
        }
        before = true
      }
      if (widgetAnchor != null) {
        val anchorIndex = ArrayUtil.indexOf(rightPanel!!.components, widgetAnchor.component)
        return if (before) anchorIndex else anchorIndex + 1
      }
    }
    return -1
  }

  private fun getTargetPanel(position: Position): JPanel {
    return when (position) {
      Position.RIGHT -> rightPanel()
      Position.LEFT -> leftPanel()
      else -> centerPanel()
    }
  }

  private fun centerPanel(): JPanel {
    var centerPanel = centerPanel
    if (centerPanel == null) {
      centerPanel = JBUI.Panels.simplePanel().andTransparent()!!
      this.centerPanel = centerPanel
      centerPanel.border = if (ExperimentalUI.isNewUI()) JBUI.Borders.empty() else JBUI.Borders.empty(0, 1)
      add(centerPanel, BorderLayout.CENTER)
    }
    return centerPanel
  }

  private fun rightPanel(): JPanel {
    var rightPanel = rightPanel
    if (rightPanel == null) {
      rightPanel = JPanel()
      this.rightPanel = rightPanel
      rightPanel.border = JBUI.Borders.emptyLeft(1)
      rightPanel.layout = object : BoxLayout(rightPanel, X_AXIS) {
        override fun layoutContainer(target: Container) {
          super.layoutContainer(target)
          for (component in target.components) {
            if (component is MemoryUsagePanel) {
              val r = component.getBounds()
              r.y = 0
              r.width += if (SystemInfo.isMac) 4 else 0
              r.height = target.height
              component.setBounds(r)
            }
          }
        }
      }
      rightPanel.isOpaque = false
      add(rightPanel, BorderLayout.EAST)
    }
    return rightPanel
  }

  private fun leftPanel(): JPanel {
    var leftPanel = leftPanel
    if (leftPanel == null) {
      leftPanel = JPanel()
      this.leftPanel = leftPanel
      leftPanel.border = JBUI.Borders.empty(0, 4, 0, 1)
      leftPanel.layout = BoxLayout(leftPanel, BoxLayout.X_AXIS)
      leftPanel.isOpaque = false
      add(leftPanel, BorderLayout.WEST)
    }
    return leftPanel
  }

  override fun setInfo(s: String?) {
    setInfo(s, null)
  }

  override fun setInfo(s: @Nls String?, requestor: String?) {
    UIUtil.invokeLaterIfNeeded {
      if (infoAndProgressPanel != null) {
        info = infoAndProgressPanel.setText(s, requestor)
      }
    }
  }

  override fun getInfo(): @NlsContexts.StatusBarText String? = info

  override fun addProgress(indicator: ProgressIndicatorEx, info: TaskInfo) {
    infoAndProgressPanel!!.addProgress(indicator, info)
  }

  override fun getBackgroundProcesses(): List<Pair<TaskInfo, ProgressIndicator>> = infoAndProgressPanel!!.backgroundProcesses

  override fun setProcessWindowOpen(open: Boolean) {
    infoAndProgressPanel!!.isProcessWindowOpen = open
  }

  override fun isProcessWindowOpen(): Boolean = infoAndProgressPanel!!.isProcessWindowOpen

  override fun startRefreshIndication(tooltipText: @NlsContexts.Tooltip String?) {
    infoAndProgressPanel!!.setRefreshVisible(tooltipText)
    updateChildren { it.startRefreshIndication(tooltipText) }
  }

  override fun stopRefreshIndication() {
    infoAndProgressPanel!!.setRefreshHidden()
    updateChildren(IdeStatusBarImpl::stopRefreshIndication)
  }

  override fun notifyProgressByBalloon(type: MessageType, htmlBody: @NlsContexts.PopupContent String): BalloonHandler {
    return notifyProgressByBalloon(type, htmlBody, null, null)
  }

  override fun notifyProgressByBalloon(type: MessageType,
                                       htmlBody: @NlsContexts.PopupContent String,
                                       icon: Icon?,
                                       listener: HyperlinkListener?): BalloonHandler {
    return infoAndProgressPanel!!.notifyByBalloon(type, htmlBody, icon, listener)
  }

  override fun fireNotificationPopup(content: JComponent, backgroundColor: Color?) {
    NotificationPopup(this, content, backgroundColor)
  }

  private fun applyWidgetEffect(component: JComponent?, widgetEffect: WidgetEffect?) {
    if (effectComponent === component &&
        (effectComponent == null || ClientProperty.get(effectComponent, WIDGET_EFFECT_KEY) == widgetEffect)) {
      return
    }

    if (effectComponent != null) {
      ClientProperty.put(effectComponent!!, WIDGET_EFFECT_KEY, null)
      repaint(RelativeRectangle(effectComponent).getRectangleOn(this))
    }

    effectComponent = component
    // widgets shall not be opaque, as it may conflict with a background image
    // the following code can be dropped in future
    if (effectComponent != null) {
      effectComponent!!.background = null
      ClientProperty.put(effectComponent!!, WIDGET_EFFECT_KEY, widgetEffect)
      if (effectComponent!!.isEnabled && widgetEffect != null) {
        effectComponent!!.background = if (widgetEffect == WidgetEffect.HOVER) JBUI.CurrentTheme.StatusBar.Widget.HOVER_BACKGROUND else JBUI.CurrentTheme.StatusBar.Widget.PRESSED_BACKGROUND
      }
      repaint(RelativeRectangle(effectComponent).getRectangleOn(this))
    }
  }

  private fun paintWidgetEffectBackground(g: Graphics) {
    if (effectComponent == null || !effectComponent!!.isEnabled) return
    if (!UIUtil.isAncestor(this, effectComponent)) return
    if (effectComponent is MemoryUsagePanel) return
    val bounds = effectComponent!!.bounds
    val point = RelativePoint(effectComponent!!.parent, bounds.location).getPoint(this)
    val widgetEffect = ClientProperty.get(effectComponent, WIDGET_EFFECT_KEY)
    val bg = if (widgetEffect == WidgetEffect.PRESSED) JBUI.CurrentTheme.StatusBar.Widget.PRESSED_BACKGROUND else JBUI.CurrentTheme.StatusBar.Widget.HOVER_BACKGROUND
    if (!ExperimentalUI.isNewUI() && getUI() is StatusBarUI) {
      point.y += StatusBarUI.BORDER_WIDTH.get()
      bounds.height -= StatusBarUI.BORDER_WIDTH.get()
    }
    g.color = bg
    g.fillRect(point.x, point.y, bounds.width, bounds.height)
  }

  override fun paintChildren(g: Graphics) {
    paintWidgetEffectBackground(g)
    super.paintChildren(g)
  }

  override fun dispatch(e: AWTEvent): Boolean {
    return if (e is MouseEvent) {
      dispatchMouseEvent(e)
    }
    else false
  }

  private fun dispatchMouseEvent(e: MouseEvent): Boolean {
    if (rightPanel == null || centerPanel == null || !rightPanel!!.isVisible) {
      return false
    }
    val component = e.component ?: return false
    if (ComponentUtil.getWindow(frame.component) !== ComponentUtil.getWindow(component)) {
      applyWidgetEffect(null, null)
      return false
    }
    val point = SwingUtilities.convertPoint(component, e.point, rightPanel)
    val widget = ObjectUtils.tryCast(rightPanel!!.getComponentAt(point), JComponent::class.java)
    if (e.clickCount == 0 || e.id == MouseEvent.MOUSE_RELEASED) {
      applyWidgetEffect(if (widget !== rightPanel) widget else null, WidgetEffect.HOVER)
    }
    else if (e.clickCount == 1 && e.id == MouseEvent.MOUSE_PRESSED) {
      applyWidgetEffect(if (widget !== rightPanel) widget else null, WidgetEffect.PRESSED)
    }
    if (e.isConsumed || widget == null) {
      return false
    }
    if (e.isPopupTrigger && (e.id == MouseEvent.MOUSE_PRESSED || e.id == MouseEvent.MOUSE_RELEASED)) {
      val project = project
      if (project != null) {
        val actionManager = ActionManager.getInstance()
        val group = ObjectUtils.tryCast(actionManager.getAction(StatusBarWidgetsActionGroup.GROUP_ID), ActionGroup::class.java)
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

  override fun getUIClassID(): String = uiClassId

  override fun updateUI() {
    if (UIManager.get(uiClassId) != null) {
      setUI(UIManager.getUI(this))
    }
    else {
      setUI(StatusBarUI())
    }
  }

  override fun getComponentGraphics(g: Graphics): Graphics {
    return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(g))
  }

  override fun removeWidget(id: String) {
    EdtInvocationManager.invokeLaterIfNeeded {
      val bean = widgetMap.remove(id)
      if (bean != null) {
        if (UIUtil.isAncestor(bean.component!!, effectComponent)) {
          ClientProperty.put(effectComponent!!, WIDGET_EFFECT_KEY, null)
          effectComponent = null
        }
        val targetPanel = getTargetPanel(bean.position!!)
        targetPanel.remove(bean.component)
        targetPanel.revalidate()
        Disposer.dispose(bean.widget!!)
        fireWidgetRemoved(id)
      }
      updateChildren { it.removeWidget(id) }
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
      updateChildren { it.updateWidget(id) }
    }
  }

  override fun getWidget(id: String): StatusBarWidget? = widgetMap.get(id)?.widget

  override val allWidgets: Collection<StatusBarWidget>?
    get() = widgetMap.values.mapNotNull { it.widget }

  override fun getWidgetAnchor(id: String): @NonNls String? = widgetMap.get(id)?.anchor

  //todo: make private after removing all external usages
  @ApiStatus.Internal
  fun getWidgetComponent(id: String): JComponent? = widgetMap.get(id)?.component

  override val project: Project?
    get() = frame.project

  override val currentEditor: Supplier<FileEditor?>?
    get() = editorProvider

  @ApiStatus.Internal
  fun setEditorProvider(provider: CurrentEditorProvider?) {
    editorProvider = if (provider == null) null else Supplier { provider.currentEditor }
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = AccessibleIdeStatusBarImpl()
    }
    return accessibleContext
  }

  override fun addListener(listener: StatusBarListener, parentDisposable: Disposable) {
    listeners.addListener(listener, parentDisposable)
  }

  private fun fireWidgetAdded(widget: StatusBarWidget, anchor: @NonNls String?) {
    listeners.multicaster.widgetAdded(widget, anchor)
  }

  private fun fireWidgetUpdated(id: @NonNls String) {
    listeners.multicaster.widgetUpdated(id)
  }

  private fun fireWidgetRemoved(id: @NonNls String) {
    listeners.multicaster.widgetRemoved(id)
  }

  private fun registerCloneTasks() {
    CloneableProjectsService.getInstance()
      .collectCloneableProjects()
      .asSequence()
      .map { (_, _, _, cloneableProject): CloneableProjectItem -> cloneableProject }
      .forEach { (_, cloneTaskInfo, progressIndicator): CloneableProject -> addProgress(progressIndicator, cloneTaskInfo) }
    ApplicationManager.getApplication().messageBus.connect(this)
      .subscribe(CloneableProjectsService.TOPIC, object : CloneProjectListener {
        override fun onCloneCanceled() {}
        override fun onCloneFailed() {}
        override fun onCloneSuccess() {}
        override fun onCloneRemoved() {}
        override fun onCloneAdded(progressIndicator: ProgressIndicatorEx, taskInfo: TaskInfo) {
          addProgress(progressIndicator, taskInfo)
        }
      })
  }

  protected inner class AccessibleIdeStatusBarImpl : AccessibleJComponent() {
    override fun getAccessibleRole(): AccessibleRole = AccessibleRole.PANEL
  }
}