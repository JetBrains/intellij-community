// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ContextHelpAction
import com.intellij.ide.actions.ToggleToolbarAction
import com.intellij.ide.actions.ToolWindowMoveAction
import com.intellij.ide.actions.ToolwindowFusEventFields
import com.intellij.ide.impl.ContentManagerWatcher
import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.FusAwareAction
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.Divider
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.*
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.toolWindow.FocusTask
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.toolWindow.ToolWindowEventSource
import com.intellij.toolWindow.ToolWindowProperty
import com.intellij.ui.ClientProperty
import com.intellij.ui.LayeredIcon
import com.intellij.ui.UIBundle
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.ui.content.impl.ContentManagerImpl
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Consumer
import com.intellij.util.ModalityUiUtil
import com.intellij.util.SingleAlarm
import com.intellij.util.ui.*
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import org.jetbrains.annotations.ApiStatus
import java.awt.AWTEvent
import java.awt.Color
import java.awt.Component
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.InputEvent
import javax.swing.*
import javax.swing.text.JTextComponent
import kotlin.math.abs

internal class ToolWindowImpl(val toolWindowManager: ToolWindowManagerImpl,
                              private val id: String,
                              private val canCloseContent: Boolean,
                              private val dumbAware: Boolean,
                              component: JComponent?,
                              private val parentDisposable: Disposable,
                              windowInfo: WindowInfo,
                              private var contentFactory: ToolWindowFactory?,
                              private var isAvailable: Boolean = true,
                              @NlsContexts.TabTitle private var stripeTitle: String) : ToolWindowEx {
  var windowInfoDuringInit: WindowInfoImpl? = null

  private val focusTask by lazy { FocusTask(this) }
  val focusAlarm by lazy { SingleAlarm(focusTask, 0, disposable) }

  override fun getId() = id

  override fun getProject() = toolWindowManager.project

  override fun getDecoration(): ToolWindowEx.ToolWindowDecoration {
    return ToolWindowEx.ToolWindowDecoration(icon, additionalGearActions)
  }

  var windowInfo: WindowInfo = windowInfo
    private set

  private var contentUi: ToolWindowContentUi? = null

  internal var decorator: InternalDecoratorImpl? = null
    private set

  private var hideOnEmptyContent = false
  var isPlaceholderMode = false

  private var pendingContentManagerListeners: MutableList<ContentManagerListener>? = null

  private val showing = object : BusyObject.Impl() {
    override fun isReady(): Boolean {
      return getComponentIfInitialized()?.isShowing ?: false
    }
  }

  private var toolWindowFocusWatcher: ToolWindowFocusWatcher? = null

  private var additionalGearActions: ActionGroup? = null

  private var helpId: String? = null

  internal var icon: ToolWindowIcon? = null

  private val contentManager = lazy {
    val result = createContentManager()
    if (toolWindowManager.isNewUi) {
      result.addContentManagerListener(UpdateBackgroundContentManager(decorator))
    }
    result
  }

  private val moveOrResizeAlarm = SingleAlarm(Runnable {
    val decorator = this@ToolWindowImpl.decorator
    if (decorator != null) {
      toolWindowManager.movedOrResized(decorator)
    }
    this@ToolWindowImpl.windowInfo = toolWindowManager.getLayout().getInfo(getId()) as WindowInfo
  }, 100, disposable)

  init {
    if (component != null) {
      val content = ContentImpl(component, "", false)
      val contentManager = contentManager.value
      contentManager.addContent(content)
      contentManager.setSelectedContent(content, false)
    }
  }

  private class UpdateBackgroundContentManager(private val decorator: InternalDecoratorImpl?) : ContentManagerListener {
    override fun contentAdded(event: ContentManagerEvent) {
      setBackgroundRecursively(event.content.component, JBUI.CurrentTheme.ToolWindow.background())
      addAdjustListener(decorator, event.content.component)
    }
  }

  internal fun getOrCreateDecoratorComponent(): InternalDecoratorImpl {
    ensureContentManagerInitialized()
    return decorator!!
  }

  fun createCellDecorator() : InternalDecoratorImpl {
    val cellContentManager = ContentManagerImpl(canCloseContent, toolWindowManager.project, parentDisposable, ContentManagerImpl.ContentUiProducer { contentManager, componentGetter ->
      ToolWindowContentUi(this, contentManager, componentGetter.get())
    })
    return InternalDecoratorImpl(this, cellContentManager.ui as ToolWindowContentUi, cellContentManager.component)
  }

  private fun createContentManager(): ContentManagerImpl {
    val contentManager = ContentManagerImpl(canCloseContent, toolWindowManager.project, parentDisposable,
                                            ContentManagerImpl.ContentUiProducer { contentManager, componentGetter ->
                                              val result = ToolWindowContentUi(this, contentManager, componentGetter.get())
                                              contentUi = result
                                              result
                                            })

    addContentNotInHierarchyComponents(contentUi!!)

    val contentComponent = contentManager.component
    InternalDecoratorImpl.installFocusTraversalPolicy(contentComponent, LayoutFocusTraversalPolicy())
    Disposer.register(parentDisposable, UiNotifyConnector.installOn(contentComponent, object : Activatable {
      override fun showNotify() {
        showing.onReady()
      }
    }))

    var decoratorChild = contentManager.component
    if (!dumbAware) {
      decoratorChild = DumbService.getInstance(toolWindowManager.project).wrapGently(decoratorChild, parentDisposable)
    }

    val decorator = InternalDecoratorImpl(this, contentUi!!, decoratorChild)
    this.decorator = decorator

    decorator.applyWindowInfo(windowInfo)
    decorator.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        onMovedOrResized()
      }
    })

    toolWindowFocusWatcher = ToolWindowFocusWatcher(toolWindow = this, component = decorator)
    contentManager.addContentManagerListener(object : ContentManagerListener {
      override fun selectionChanged(event: ContentManagerEvent) {
        this@ToolWindowImpl.decorator?.headerToolbar?.updateActionsImmediately()
      }
    })

    // after init, as it was before contentManager creation was changed to be lazy
    pendingContentManagerListeners?.let { list ->
      pendingContentManagerListeners = null
      for (listener in list) {
        contentManager.addContentManagerListener(listener)
      }
    }

    return contentManager
  }

  fun onMovedOrResized() {
    moveOrResizeAlarm.cancelAndRequest()
  }

  internal fun setWindowInfoSilently(info: WindowInfo) {
    windowInfo = info
  }

  internal fun applyWindowInfo(info: WindowInfo) {
    windowInfo = info
    contentUi?.setType(info.contentUiType)
    val decorator = decorator
    if (decorator != null) {
      decorator.applyWindowInfo(info)
      decorator.validate()
      decorator.repaint()
    }
  }

  val decoratorComponent: JComponent?
    get() = decorator

  val hasFocus: Boolean
    get() = decorator?.hasFocus() ?: false

  fun setFocusedComponent(component: Component) {
    toolWindowFocusWatcher?.setFocusedComponentImpl(component)
  }

  fun getLastFocusedContent() : Content? {
    val lastFocusedComponent = toolWindowFocusWatcher?.focusedComponent
    if (lastFocusedComponent is JComponent) {
      if (!lastFocusedComponent.isShowing) return null
      val nearestDecorator = InternalDecoratorImpl.findNearestDecorator(lastFocusedComponent)
      val content = nearestDecorator?.contentManager?.getContent(lastFocusedComponent)
      if (content != null && content.isSelected) return content
    }
    return null
  }

  override fun getDisposable() = parentDisposable

  override fun remove() {
    @Suppress("DEPRECATION")
    toolWindowManager.unregisterToolWindow(id)
  }

  override fun activate(runnable: Runnable?, autoFocusContents: Boolean, forced: Boolean) {
    toolWindowManager.activateToolWindow(id, runnable, autoFocusContents)
  }

  override fun isActive(): Boolean {
    return windowInfo.isVisible && decorator != null && toolWindowManager.activeToolWindowId == id
  }

  override fun getReady(requestor: Any): ActionCallback {
    val result = ActionCallback()
    showing.getReady(this)
      .doWhenDone {
        toolWindowManager.focusManager.doWhenFocusSettlesDown {
          if (contentManager.isInitialized() && contentManager.value.isDisposed) {
            return@doWhenFocusSettlesDown
          }
          contentManager.value.getReady(requestor).notify(result)
        }
      }
    return result
  }

  override fun show(runnable: Runnable?) {
    EDT.assertIsEdt()
    toolWindowManager.showToolWindow(id)
    callLater(runnable)
  }

  override fun hide(runnable: Runnable?) {
    toolWindowManager.hideToolWindow(id)
    callLater(runnable)
  }

  override fun isVisible() = windowInfo.isVisible

  override fun getAnchor() = windowInfo.anchor

  override fun setAnchor(anchor: ToolWindowAnchor, runnable: Runnable?) {
    EDT.assertIsEdt()
    toolWindowManager.setToolWindowAnchor(id, anchor)
    callLater(runnable)
  }

  override fun isSplitMode() = windowInfo.isSplit

  override fun setContentUiType(type: ToolWindowContentUiType, runnable: Runnable?) {
    EDT.assertIsEdt()
    toolWindowManager.setContentUiType(id, type)
    callLater(runnable)
  }

  override fun setDefaultContentUiType(type: ToolWindowContentUiType) {
    toolWindowManager.setDefaultContentUiType(this, type)
  }

  override fun getContentUiType() = windowInfo.contentUiType

  override fun setSplitMode(isSideTool: Boolean, runnable: Runnable?) {
    EDT.assertIsEdt()
    toolWindowManager.setSideTool(id, isSideTool)
    callLater(runnable)
  }

  fun setSideToolAndAnchor(anchor: ToolWindowAnchor, split: Boolean, order: Int) {
    toolWindowManager.setSideToolAndAnchor(id, WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID, anchor, order, split)
  }

  override fun setAutoHide(value: Boolean) {
    toolWindowManager.setToolWindowAutoHide(id, value)
  }

  override fun isAutoHide() = windowInfo.isAutoHide

  override fun getType() = windowInfo.type

  override fun setType(type: ToolWindowType, runnable: Runnable?) {
    EDT.assertIsEdt()
    toolWindowManager.setToolWindowType(id, type)
    callLater(runnable)
  }

  override fun getInternalType() = windowInfo.internalType

  override fun stretchWidth(value: Int) {
    toolWindowManager.stretchWidth(this, value)
  }

  override fun stretchHeight(value: Int) {
    toolWindowManager.stretchHeight(this, value)
  }

  override fun getDecorator(): InternalDecoratorImpl = decorator!!

  override fun setAdditionalGearActions(value: ActionGroup?) {
    additionalGearActions = value
  }

  override fun setTitleActions(actions: List<AnAction>) {
    ensureContentManagerInitialized()
    decorator!!.setTitleActions(actions)
  }

  override fun setTabActions(vararg actions: AnAction) {
    createContentIfNeeded()
    decorator!!.setTabActions(actions.toList())
  }

  override fun setTabDoubleClickActions(actions: List<AnAction>) {
    contentUi?.setTabDoubleClickActions(actions)
  }

  override fun setAvailable(value: Boolean) {
    if (isAvailable == value) {
      return
    }

    if (windowInfoDuringInit != null) {
      throw IllegalStateException("Do not use toolWindow.setAvailable() as part of ToolWindowFactory.init().\n" +
                                  "Use ToolWindowFactory.shouldBeAvailable() instead.")
    }

    toolWindowManager.assertIsEdt()

    isAvailable = value
    if (value) {
      toolWindowManager.toolWindowAvailable(this)
    }
    else {
      toolWindowManager.toolWindowUnavailable(this)
      contentUi?.dropCaches()
    }
  }

  override fun setAvailable(value: Boolean, runnable: Runnable?) {
    setAvailable(value)
    callLater(runnable)
  }

  private fun callLater(runnable: Runnable?) {
    if (runnable != null) {
      toolWindowManager.invokeLater(runnable)
    }
  }

  override fun installWatcher(contentManager: ContentManager) {
    ContentManagerWatcher.watchContentManager(this, contentManager)
  }

  override fun isAvailable() = isAvailable

  override fun getComponent(): JComponent {
    if (toolWindowManager.project.isDisposed) {
      // nullable because of TeamCity plugin
      @Suppress("HardCodedStringLiteral")
      return JLabel("Do not call getComponent() on dispose")
    }
    return contentManager.value.component
  }

  fun getComponentIfInitialized(): JComponent? {
    return if (contentManager.isInitialized()) contentManager.value.takeIf { !it.isDisposed }?.component else null
  }

  override fun getContentManagerIfCreated(): ContentManager? {
    return if (contentManager.isInitialized()) contentManager.value else null
  }

  override fun getContentManager(): ContentManager {
    createContentIfNeeded()
    return contentManager.value
  }

  override fun addContentManagerListener(listener: ContentManagerListener) {
    if (contentManager.isInitialized()) {
      contentManager.value.addContentManagerListener(listener)
    }
    else {
      if (pendingContentManagerListeners == null) {
        pendingContentManagerListeners = mutableListOf()
      }
      pendingContentManagerListeners!!.add(listener)
    }
  }

  override fun canCloseContents() = canCloseContent

  override fun getIcon() = icon

  override fun getTitle(): String? = contentManager.value.selectedContent?.displayName

  override fun getStripeTitle() = stripeTitle

  override fun setIcon(newIcon: Icon) {
    EDT.assertIsEdt()
    if (newIcon !== icon?.retrieveIcon()) {
      doSetIcon(newIcon)
      toolWindowManager.toolWindowPropertyChanged(this, ToolWindowProperty.ICON)
    }
  }

  internal fun doSetIcon(newIcon: Icon) {
    val oldIcon = icon
    if (oldIcon !== newIcon &&
        newIcon !is LayeredIcon &&
        !toolWindowManager.isNewUi &&
        (abs(newIcon.iconHeight - JBUIScale.scale(13f)) >= 1 || abs(newIcon.iconWidth - JBUIScale.scale(13f)) >= 1)) {
      logger<ToolWindowImpl>().warn("ToolWindow icons should be 13x13, but got: ${newIcon.iconWidth}x${newIcon.iconHeight}. Please fix ToolWindow (ID:  $id) or icon $newIcon")
    }
    icon = ToolWindowIcon(newIcon, id)
  }

  override fun setTitle(title: String) {
    EDT.assertIsEdt()
    contentManager.value.selectedContent?.displayName = title
    toolWindowManager.toolWindowPropertyChanged(this, ToolWindowProperty.TITLE)
  }

  override fun setStripeTitle(value: String) {
    if (value == stripeTitle) {
      return
    }

    stripeTitle = value
    contentUi?.update()

    if (windowInfoDuringInit == null) {
      EDT.assertIsEdt()
      toolWindowManager.toolWindowPropertyChanged(toolWindow = this, property = ToolWindowProperty.STRIPE_TITLE)
    }
  }

  fun fireActivated(source: ToolWindowEventSource) {
    toolWindowManager.activated(this, source)
  }

  fun fireHidden(source: ToolWindowEventSource?) {
    toolWindowManager.hideToolWindow(id = id, source = source)
  }

  fun fireHiddenSide(source: ToolWindowEventSource?) {
    toolWindowManager.hideToolWindow(id = id, hideSide = true, source = source)
  }

  override fun setDefaultState(anchor: ToolWindowAnchor?, type: ToolWindowType?, floatingBounds: Rectangle?) {
    toolWindowManager.setDefaultState(this, anchor, type, floatingBounds)
  }

  override fun setToHideOnEmptyContent(value: Boolean) {
    hideOnEmptyContent = value
  }

  fun isToHideOnEmptyContent() = hideOnEmptyContent

  override fun setShowStripeButton(value: Boolean) {
    val windowInfoDuringInit = windowInfoDuringInit
    if (windowInfoDuringInit == null) {
      toolWindowManager.setShowStripeButton(id, value)
    }
    else {
      windowInfoDuringInit.isShowStripeButton = value
    }
  }

  override fun isShowStripeButton() = windowInfo.isShowStripeButton

  override fun isDisposed() = contentManager.isInitialized() && contentManager.value.isDisposed

  private fun ensureContentManagerInitialized() {
    contentManager.value
  }

  internal fun scheduleContentInitializationIfNeeded() {
    if (contentFactory != null) {
      // todo use lazy loading (e.g. JBLoadingPanel)
      createContentIfNeeded()
    }
  }

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("Do not use. Tool window content will be initialized automatically.", level = DeprecationLevel.ERROR)
  @ApiStatus.ScheduledForRemoval
  fun ensureContentInitialized() {
    createContentIfNeeded()
  }

  private fun createContentIfNeeded() {
    val currentContentFactory = contentFactory ?: return
    // clear it first to avoid SOE
    this.contentFactory = null
    if (contentManager.isInitialized()) {
      contentManager.value.removeAllContents(false)
    }
    else {
      ensureContentManagerInitialized()
    }
    currentContentFactory.createToolWindowContent(toolWindowManager.project, this)

    if (toolWindowManager.isNewUi) {
      setBackgroundRecursively(contentManager.value.component, JBUI.CurrentTheme.ToolWindow.background())
      addAdjustListener(decorator, contentManager.value.component)
    }
  }

  override fun getHelpId() = helpId

  override fun setHelpId(value: String) {
    helpId = value
  }

  override fun showContentPopup(inputEvent: InputEvent) {
    // called only when tool window is already opened, so, content should be already created
    ToolWindowContentUi.toggleContentPopup(contentUi!!, contentManager.value)
  }

  @JvmOverloads
  fun createPopupGroup(skipHideAction: Boolean = false): ActionGroup {
    val group = GearActionGroup()
    if (!skipHideAction) {
      group.addSeparator()
      group.add(HideAction())
    }
    group.addSeparator()
    group.add(object : ContextHelpAction() {
      override fun getHelpId(dataContext: DataContext): String? {
        val content = contentManagerIfCreated?.selectedContent
        if (content != null) {
          val helpId = content.helpId
          if (helpId != null) {
            return helpId
          }
        }

        val id = getHelpId()
        if (id != null) {
          return id
        }

        val context = if (content == null) dataContext else DataManager.getInstance().getDataContext(content.component)
        return super.getHelpId(context)
      }

      override fun update(e: AnActionEvent) {
        super.update(e)

        e.presentation.isEnabledAndVisible = getHelpId(e.dataContext) != null
      }
    })
    return group
  }

  override fun getEmptyText(): StatusText = (contentManager.value.component as ComponentWithEmptyText).emptyText

  fun setEmptyStateBackground(color: Color) {
    decorator?.background = color
  }

  private inner class GearActionGroup : DefaultActionGroup(), DumbAware {
    init {
      templatePresentation.icon = AllIcons.General.GearPlain
      if (toolWindowManager.isNewUi) {
        templatePresentation.icon = AllIcons.Actions.More
      }
      templatePresentation.text = IdeBundle.message("show.options.menu")
      val additionalGearActions = additionalGearActions
      if (additionalGearActions != null) {
        if (additionalGearActions.isPopup && !additionalGearActions.templatePresentation.text.isNullOrEmpty()) {
          add(additionalGearActions)
        }
        else {
          addSorted(this, additionalGearActions)
        }
        addSeparator()
      }

      val toggleToolbarGroup = ToggleToolbarAction.createToggleToolbarGroup(toolWindowManager.project, this@ToolWindowImpl)
      if (ToolWindowId.PREVIEW != id) {
        toggleToolbarGroup.addAction(ToggleContentUiTypeAction())
      }

      addAction(toggleToolbarGroup).setAsSecondary(true)
      add(ActionManager.getInstance().getAction("TW.ViewModeGroup"))
      if (toolWindowManager.isNewUi) {
        add(SquareStripeButton.createMoveGroup())
      }
      else {
        add(ToolWindowMoveAction.Group())
      }
      add(ResizeActionGroup())
      addSeparator()
      add(RemoveStripeButtonAction())
    }
  }

  private inner class HideAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
      toolWindowManager.hideToolWindow(id, false)
    }

    override fun update(event: AnActionEvent) {
      val presentation = event.presentation
      presentation.isEnabled = isVisible
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }

    init {
      ActionUtil.copyFrom(this, InternalDecoratorImpl.HIDE_ACTIVE_WINDOW_ACTION_ID)
      templatePresentation.text = UIBundle.message("tool.window.hide.action.name")
    }
  }

  private inner class ResizeActionGroup : DefaultActionGroup(
    ActionsBundle.groupText("ResizeToolWindowGroup"),
    ActionManager.getInstance().let { actionManager ->
      listOf(
        actionManager.getAction("ResizeToolWindowLeft"),
        actionManager.getAction("ResizeToolWindowRight"),
        actionManager.getAction("ResizeToolWindowUp"),
        actionManager.getAction("ResizeToolWindowDown"),
        actionManager.getAction("MaximizeToolWindow")
      )
    }) {
    init {
      isPopup = true
    }
  }

  private inner class RemoveStripeButtonAction :
    AnAction(ActionsBundle.messagePointer("action.RemoveStripeButton.text"),
             ActionsBundle.messagePointer("action.RemoveStripeButton.description"),
             null), DumbAware, FusAwareAction {
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = isShowStripeButton
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun actionPerformed(e: AnActionEvent) {
      toolWindowManager.hideToolWindow(id, removeFromStripe = true, source = ToolWindowEventSource.RemoveStripeButtonAction)
    }

    override fun getAdditionalUsageData(event: AnActionEvent): List<EventPair<*>> {
      return listOf(ToolwindowFusEventFields.TOOLWINDOW with id)
    }
  }

  private inner class ToggleContentUiTypeAction : ToggleAction(), DumbAware, FusAwareAction {
    private var hadSeveralContents = false

    init {
      ActionUtil.copyFrom(this, "ToggleContentUiTypeMode")
    }

    override fun update(e: AnActionEvent) {
      hadSeveralContents = hadSeveralContents || (contentManager.isInitialized() && contentManager.value.contentCount > 1)
      super.update(e)
      e.presentation.isVisible = hadSeveralContents
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      return windowInfo.contentUiType === ToolWindowContentUiType.COMBO
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      toolWindowManager.setContentUiType(id, if (state) ToolWindowContentUiType.COMBO else ToolWindowContentUiType.TABBED)
    }

    override fun getAdditionalUsageData(event: AnActionEvent): List<EventPair<*>> {
      return listOf(ToolwindowFusEventFields.TOOLWINDOW with id)
    }
  }

  fun requestFocusInToolWindow() {
    focusTask.resetStartTime()
    focusAlarm.cancelAllRequests()
    focusTask.run()
  }
}

private fun addSorted(main: DefaultActionGroup, group: ActionGroup) {
  val children = group.getChildren(null)
  var hadSecondary = false
  for (action in children) {
    if (group.isPrimary(action)) {
      main.add(action)
    }
    else {
      hadSecondary = true
    }
  }
  if (hadSecondary) {
    main.addSeparator()
    for (action in children) {
      if (!group.isPrimary(action)) {
        main.addAction(action).setAsSecondary(true)
      }
    }
  }
  val separatorText = group.templatePresentation.text
  if (children.isNotEmpty() && !separatorText.isNullOrEmpty()) {
    main.addAction(Separator(separatorText), Constraints.FIRST)
  }
}

private fun addContentNotInHierarchyComponents(contentUi: ToolWindowContentUi) {
  contentUi.component.putClientProperty(UIUtil.NOT_IN_HIERARCHY_COMPONENTS, NotInHierarchyComponents(contentUi))
}

private class NotInHierarchyComponents(val contentUi: ToolWindowContentUi) : Iterable<Component> {
  private val oldProperty = ClientProperty.get(contentUi.component, UIUtil.NOT_IN_HIERARCHY_COMPONENTS)

  override fun iterator(): Iterator<Component> {
    var result = emptySequence<Component>()

    if (oldProperty != null) {
      result += oldProperty.asSequence()
    }

    val contentManager = contentUi.contentManager
    if (contentManager.contentCount != 0) {
      result += contentManager.contents
        .asSequence()
        .mapNotNull { content: Content ->
          var last: JComponent? = null
          var parent: Component? = content.component
          while (parent != null) {
            if (parent === contentUi.component || parent !is JComponent) {
              return@mapNotNull null
            }
            last = parent
            parent = parent.getParent()
          }
          last
        }
    }

    return result.iterator()
  }
}

/**
 * Notifies window manager about focus traversal in a tool window
 */
private class ToolWindowFocusWatcher(private val toolWindow: ToolWindowImpl, component: JComponent) : FocusWatcher() {
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
        ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(), toolWindowManager.project.disposed) {
          val entry = toolWindowManager.getEntry(id) ?: return@invokeLaterIfNeeded
          val windowInfo = entry.readOnlyWindowInfo
          if (!windowInfo.isVisible) {
            return@invokeLaterIfNeeded
          }

          toolWindowManager.activateToolWindow(entry = entry,
                                               info = toolWindowManager.getRegisteredMutableInfoOrLogError(entry.id),
                                               autoFocusContents = false)
        }
      })
  }
}

private fun setBackgroundRecursively(component: Component, bg: Color) {
  UIUtil.forEachComponentInHierarchy(component, Consumer { c ->
    if (c !is ActionButton && c !is Divider && c !is JTextComponent) {
      c.background = bg
    }
  })
}

private fun addAdjustListener(decorator: InternalDecoratorImpl?, component: JComponent) {
  UIUtil.findComponentOfType(component, JScrollPane::class.java)?.verticalScrollBar?.addAdjustmentListener { event ->
    decorator?.let {
      ClientProperty.put(it, SimpleToolWindowPanel.SCROLLED_STATE, event.adjustable?.value != 0)
      it.header.repaint()
    }
  }
}