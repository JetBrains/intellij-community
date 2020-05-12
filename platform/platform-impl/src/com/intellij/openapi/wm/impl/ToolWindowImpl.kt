// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.*
import com.intellij.ide.impl.ContentManagerWatcher
import com.intellij.idea.ActionsBundle
import com.intellij.internal.statistic.eventLog.EventPair
import com.intellij.notification.EventLog
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.FusAwareAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.BusyObject
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.LayeredIcon
import com.intellij.ui.UIBundle
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.ui.content.impl.ContentManagerImpl
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.SingleAlarm
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import java.awt.Component
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.InputEvent
import java.util.*
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.LayoutFocusTraversalPolicy
import kotlin.math.abs

private val LOG = logger<ToolWindowImpl>()

internal class ToolWindowImpl(val toolWindowManager: ToolWindowManagerImpl,
                              private val id: String,
                              private val canCloseContent: Boolean,
                              private val dumbAware: Boolean,
                              component: JComponent?,
                              private val parentDisposable: Disposable,
                              windowInfo: WindowInfo,
                              private var contentFactory: ToolWindowFactory?,
                              private var isAvailable: Boolean = true,
                              private var stripeTitle: String) : ToolWindowEx {
  var windowInfoDuringInit: WindowInfoImpl? = null

  private val focusTask by lazy { FocusTask(this) }
  val focusAlarm by lazy { SingleAlarm(focusTask, 0, disposable) }

  override fun getId() = id

  override fun getProject() = toolWindowManager.project

  var windowInfo: WindowInfo = windowInfo
    private set

  private var contentUi: ToolWindowContentUi? = null

  private var decorator: InternalDecorator? = null

  private var hideOnEmptyContent = false
  var isPlaceholderMode = false

  private var pendingContentManagerListeners: MutableList<ContentManagerListener>? = null

  private val showing = object : BusyObject.Impl() {
    override fun isReady(): Boolean {
      return getComponentIfInitialized()?.isShowing ?: false
    }
  }

  private var toolWindowFocusWatcher: ToolWindowManagerImpl.ToolWindowFocusWatcher? = null

  private var additionalGearActions: ActionGroup? = null

  private var helpId: String? = null

  internal var icon: ToolWindowIcon? = null

  private val contentManager = lazy {
    createContentManager()
  }

  init {
    if (component != null) {
      val content = ContentImpl(component, "", false)
      val contentManager = contentManager.value
      contentManager.addContent(content)
      contentManager.setSelectedContent(content, false)
    }
  }

  internal fun getOrCreateDecoratorComponent(): JComponent {
    ensureContentManagerInitialized()
    return decorator!!
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
    InternalDecorator.installFocusTraversalPolicy(contentComponent, LayoutFocusTraversalPolicy())
    Disposer.register(parentDisposable, UiNotifyConnector(contentComponent, object : Activatable {
      override fun showNotify() {
        showing.onReady()
      }
    }))

    var decoratorChild = contentManager.component
    if (!dumbAware) {
      decoratorChild = DumbService.getInstance(toolWindowManager.project).wrapGently(decoratorChild, parentDisposable)
    }

    val decorator = InternalDecorator(this, contentUi!!, decoratorChild)
    this.decorator = decorator

    decorator.applyWindowInfo(windowInfo)
    decorator.addComponentListener(object : ComponentAdapter() {
      private val alarm = SingleAlarm(Runnable {
        toolWindowManager.resized(decorator)
      }, 100, disposable)

      override fun componentResized(e: ComponentEvent) {
        alarm.cancelAndRequest()
      }
    })

    toolWindowFocusWatcher = ToolWindowManagerImpl.ToolWindowFocusWatcher(this, contentComponent)

    // after init, as it was before contentManager creation was changed to be lazy
    pendingContentManagerListeners?.let { list ->
      pendingContentManagerListeners = null
      for (listener in list) {
        contentManager.addContentManagerListener(listener)
      }
    }

    return contentManager
  }

  internal fun applyWindowInfo(info: WindowInfo) {
    if (windowInfo == info) {
      return
    }

    windowInfo = info
    val decorator = decorator
    contentUi?.setType(info.contentUiType)
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

  @Deprecated(message = "Do not use.", level = DeprecationLevel.ERROR)
  fun getContentUI() = contentUi

  override fun getDisposable() = parentDisposable

  override fun remove() {
    @Suppress("DEPRECATION")
    toolWindowManager.unregisterToolWindow(id)
  }

  override fun activate(runnable: Runnable?) {
    activate(runnable, autoFocusContents = true)
  }

  override fun activate(runnable: Runnable?, autoFocusContents: Boolean) {
    activate(runnable, autoFocusContents, true)
  }

  override fun activate(runnable: Runnable?, autoFocusContents: Boolean, forced: Boolean) {
    toolWindowManager.activateToolWindow(id, runnable, autoFocusContents)
  }

  override fun isActive(): Boolean {
    EDT.assertIsEdt()
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
    EDT.assertIsEdt()
    toolWindowManager.hideToolWindow(id, false)
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

  override fun getDecorator() = decorator!!

  override fun setAdditionalGearActions(value: ActionGroup?) {
    additionalGearActions = value
  }

  override fun setTitleActions(actions: List<AnAction>) {
    ensureContentManagerInitialized()
    decorator!!.setTitleActions(actions)
  }

  override fun setTabActions(vararg actions: AnAction) {
    createContentIfNeeded()
    decorator!!.setTabActions(actions)
  }

  override fun setTabDoubleClickActions(actions: List<AnAction>) {
    contentUi?.setTabDoubleClickActions(actions)
  }

  override fun setAvailable(value: Boolean) {
    EDT.assertIsEdt()

    if (isAvailable != value) {
      isAvailable = value
      toolWindowManager.toolWindowPropertyChanged(this, ToolWindowProperty.AVAILABLE)

      if (!value) {
        contentUi?.dropCaches()
      }
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
    return if (contentManager.isInitialized()) contentManager.value.component else null
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
        pendingContentManagerListeners = arrayListOf()
      }
      pendingContentManagerListeners!!.add(listener)
    }
  }

  fun canCloseContents() = canCloseContent

  override fun getIcon(): Icon? {
    EDT.assertIsEdt()
    return icon
  }

  override fun getTitle(): String? {
    EDT.assertIsEdt()
    return contentManager.value.selectedContent?.displayName
  }

  override fun getStripeTitle(): String {
    EDT.assertIsEdt()
    return stripeTitle
  }

  override fun setIcon(newIcon: Icon) {
    EDT.assertIsEdt()
    doSetIcon(newIcon)
    toolWindowManager.toolWindowPropertyChanged(this, ToolWindowProperty.ICON)
  }

  internal fun doSetIcon(newIcon: Icon) {
    val oldIcon = icon
    if (EventLog.LOG_TOOL_WINDOW_ID != id) {
      if (oldIcon !== newIcon &&
          newIcon !is LayeredIcon &&
          (abs(newIcon.iconHeight - JBUIScale.scale(13f)) >= 1 || abs(newIcon.iconWidth - JBUIScale.scale(13f)) >= 1)) {
        LOG.warn("ToolWindow icons should be 13x13. Please fix ToolWindow (ID:  $id) or icon $newIcon")
      }
    }
    icon = ToolWindowIcon(newIcon, id)
  }

  override fun setTitle(title: String) {
    EDT.assertIsEdt()
    val selected = contentManager.value.selectedContent
    if (selected != null) {
      selected.displayName = title
    }
    toolWindowManager.toolWindowPropertyChanged(this, ToolWindowProperty.TITLE)
  }

  override fun setStripeTitle(value: String) {
    EDT.assertIsEdt()
    if (value != stripeTitle) {
      stripeTitle = value
      toolWindowManager.toolWindowPropertyChanged(this, ToolWindowProperty.STRIPE_TITLE)
    }
  }

  fun fireActivated() {
    toolWindowManager.activated(this)
  }

  fun fireHidden() {
    toolWindowManager.hideToolWindow(id, false)
  }

  fun fireHiddenSide() {
    toolWindowManager.hideToolWindow(id, true)
  }

  val popupGroup: ActionGroup?
    get() = createPopupGroup()

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
  fun ensureContentInitialized() {
    createContentIfNeeded()
  }

  internal fun createContentIfNeeded() {
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

  private inner class GearActionGroup : DefaultActionGroup(), DumbAware {
    init {
      templatePresentation.icon = AllIcons.General.GearPlain
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
      addSeparator()
      add(ToolWindowViewModeAction.Group())
      add(ToolWindowMoveAction.Group())
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

    init {
      ActionUtil.copyFrom(this, InternalDecorator.HIDE_ACTIVE_WINDOW_ACTION_ID)
      templatePresentation.text = UIBundle.message("tool.window.hide.action.name")
    }
  }

  private inner class ResizeActionGroup : ActionGroup(ActionsBundle.groupText("ResizeToolWindowGroup"), true), DumbAware {
    private val children by lazy<Array<AnAction>> {
      // force creation
      createContentIfNeeded()
      val component = decorator
      val toolWindow = this@ToolWindowImpl
      arrayOf(
        ResizeToolWindowAction.Left(toolWindow, component),
        ResizeToolWindowAction.Right(toolWindow, component),
        ResizeToolWindowAction.Up(toolWindow, component),
        ResizeToolWindowAction.Down(toolWindow, component),
        ActionManager.getInstance().getAction("MaximizeToolWindow")
      )
    }

    override fun getChildren(e: AnActionEvent?) = children

    override fun isDumbAware() = true
  }

  private inner class RemoveStripeButtonAction : AnAction(ActionsBundle.message("action.RemoveStripeButton.text"),
                                                          ActionsBundle.message("action.RemoveStripeButton.description"), null), DumbAware, FusAwareAction {
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = isShowStripeButton
    }

    override fun actionPerformed(e: AnActionEvent) {
      toolWindowManager.removeFromSideBar(id)
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
    val alarm = focusAlarm
    alarm.cancelAllRequests()
    alarm.request(delay = 0)
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
  UIUtil.putClientProperty(contentUi.component, UIUtil.NOT_IN_HIERARCHY_COMPONENTS, object : Iterable<JComponent> {
    override fun iterator(): Iterator<JComponent> {
      val contentManager = contentUi.contentManager
      if (contentManager.contentCount == 0) {
        return Collections.emptyIterator()
      }

      return contentManager.contents
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
        .iterator()
    }
  })
}