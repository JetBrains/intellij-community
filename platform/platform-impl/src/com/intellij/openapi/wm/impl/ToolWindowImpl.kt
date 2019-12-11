// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.ide.UiActivity
import com.intellij.ide.UiActivityMonitor
import com.intellij.ide.impl.ContentManagerWatcher
import com.intellij.notification.EventLog
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.BusyObject
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.commands.FinalizableCommand
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.LayeredIcon
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.ui.content.impl.ContentManagerImpl
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ObjectUtils
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import java.awt.Rectangle
import java.awt.event.InputEvent
import java.util.*
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.LayoutFocusTraversalPolicy
import kotlin.math.abs

private val LOG = Logger.getInstance(ToolWindowImpl::class.java)

class ToolWindowImpl internal constructor(val toolWindowManager: ToolWindowManagerImpl,
                                          val id: String,
                                          canCloseContent: Boolean,
                                          component: JComponent?,
                                          private val parentDisposable: Disposable) : ToolWindowEx {
  private val component: JComponent
  private var isAvailable = true
  private val contentManager: ContentManager
  private var icon: Icon? = null
  private var stripeTitle: String? = null
  private val contentUi = ToolWindowContentUi(this)
  private var decorator: InternalDecorator? = null
  private var hideOnEmptyContent = false
  var isPlaceholderMode = false
  private var contentFactory: ToolWindowFactory? = null
  private val myShowing = object : BusyObject.Impl() {
    override fun isReady() = component != null && component.isShowing
  }

  private var helpId: String? = null

  init {
    contentManager = ContentManagerImpl(contentUi, canCloseContent, toolWindowManager.project, parentDisposable)
    if (component != null) {
      val content = ContentImpl(component, "", false)
      contentManager.addContent(content)
      contentManager.setSelectedContent(content, false)
    }

    this.component = contentManager.component
    InternalDecorator.installFocusTraversalPolicy(this.component, LayoutFocusTraversalPolicy())

    Disposer.register(parentDisposable, UiNotifyConnector(this.component, object : Activatable {
      override fun showNotify() {
        myShowing.onReady()
      }
    }))
  }

  fun getContentUI() = contentUi

  override fun getDisposable() = parentDisposable

  override fun remove() {
    toolWindowManager.doUnregisterToolWindow(id)
  }

  override fun activate(runnable: Runnable?) {
    activate(runnable, true)
  }

  override fun activate(runnable: Runnable?, autoFocusContents: Boolean) {
    activate(runnable, autoFocusContents, true)
  }

  override fun activate(runnable: Runnable?, autoFocusContents: Boolean, forced: Boolean) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val activity: UiActivity = UiActivity.Focus("toolWindow:$id")
    UiActivityMonitor.getInstance().addActivity(toolWindowManager.project, activity, ModalityState.NON_MODAL)
    toolWindowManager.activateToolWindow(id, forced, autoFocusContents)
    toolWindowManager.invokeLater(Runnable {
      runnable?.run()
      UiActivityMonitor.getInstance().removeActivity(toolWindowManager.project, activity)
    })
  }

  override fun isActive(): Boolean {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val frame = WindowManagerEx.getInstanceEx().getFrame(toolWindowManager.project)
    if (frame == null || !frame.isActive || toolWindowManager.isEditorComponentActive) {
      return false
    }

    val actionManager = ActionManager.getInstance()
    if (actionManager is ActionManagerImpl && !actionManager.isActionPopupStackEmpty && !actionManager.isToolWindowContextMenuVisible) {
      return false
    }
    else {
      return toolWindowManager.isToolWindowActive(id) || (decorator?.isFocused ?: false)
    }
  }

  override fun getReady(requestor: Any): ActionCallback {
    val result = ActionCallback()
    myShowing.getReady(this)
      .doWhenDone {
        val cmd = ArrayList<FinalizableCommand>()
        cmd.add(object : FinalizableCommand(null) {
          override fun willChangeState(): Boolean {
            return false
          }

          override fun run() {
            IdeFocusManager.getInstance(toolWindowManager.project).doWhenFocusSettlesDown {
              if (contentManager.isDisposed) return@doWhenFocusSettlesDown
              contentManager.getReady(requestor).notify(result)
            }
          }
        })
        toolWindowManager.execute(cmd)
      }
    return result
  }

  override fun show(runnable: Runnable?) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    toolWindowManager.showToolWindow(id)
    callLater(runnable)
  }

  override fun hide(runnable: Runnable?) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    toolWindowManager.hideToolWindow(id, false)
    callLater(runnable)
  }

  override fun isVisible(): Boolean {
    return toolWindowManager.isToolWindowRegistered(id) && toolWindowManager.isToolWindowVisible(id)
  }

  override fun getAnchor(): ToolWindowAnchor {
    return toolWindowManager.getToolWindowAnchor(id)
  }

  override fun setAnchor(anchor: ToolWindowAnchor, runnable: Runnable?) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    toolWindowManager.setToolWindowAnchor(id, anchor)
    callLater(runnable)
  }

  override fun isSplitMode(): Boolean {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return toolWindowManager.isSplitMode(id)
  }

  override fun setContentUiType(type: ToolWindowContentUiType, runnable: Runnable?) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    toolWindowManager.setContentUiType(id, type)
    callLater(runnable)
  }

  override fun setDefaultContentUiType(type: ToolWindowContentUiType) {
    toolWindowManager.setDefaultContentUiType(this, type)
  }

  override fun getContentUiType(): ToolWindowContentUiType {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return toolWindowManager.getContentUiType(id)
  }

  override fun setSplitMode(isSideTool: Boolean, runnable: Runnable?) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    toolWindowManager.setSideTool(id, isSideTool)
    callLater(runnable)
  }

  override fun setAutoHide(state: Boolean) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    toolWindowManager.setToolWindowAutoHide(id, state)
  }

  override fun isAutoHide(): Boolean {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return toolWindowManager.isToolWindowAutoHide(id)
  }

  override fun getType(): ToolWindowType {
    return toolWindowManager.getToolWindowType(id)
  }

  override fun setType(type: ToolWindowType, runnable: Runnable?) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    toolWindowManager.setToolWindowType(id, type)
    callLater(runnable)
  }

  override fun getInternalType(): ToolWindowType {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return toolWindowManager.getToolWindowInternalType(id)
  }

  override fun stretchWidth(value: Int) {
    toolWindowManager.stretchWidth(this, value)
  }

  override fun stretchHeight(value: Int) {
    toolWindowManager.stretchHeight(this, value)
  }

  override fun getDecorator() = decorator!!

  override fun setAdditionalGearActions(additionalGearActions: ActionGroup?) {
    decorator?.setAdditionalGearActions(additionalGearActions)
  }

  override fun setTitleActions(vararg actions: AnAction) {
    decorator?.setTitleActions(actions)
  }

  override fun setTabActions(vararg actions: AnAction) {
    decorator?.setTabActions(actions)
  }

  fun setTabDoubleClickActions(vararg actions: AnAction) {
    contentUi.setTabDoubleClickActions(*actions)
  }

  override fun setAvailable(available: Boolean, runnable: Runnable?) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    isAvailable = available
    toolWindowManager.toolWindowPropertyChanged(this, ToolWindowEx.PROP_AVAILABLE)
    callLater(runnable)
  }

  private fun callLater(runnable: Runnable?) {
    if (runnable != null) {
      toolWindowManager.invokeLater(runnable)
    }
  }

  override fun installWatcher(contentManager: ContentManager) {
    ContentManagerWatcher(this, contentManager)
  }

  /**
   * @return `true` if the component passed into constructor is not instance of
   * `ContentManager` class. Otherwise it delegates the functionality to the
   * passed content manager.
   */
  override fun isAvailable(): Boolean {
    return isAvailable && component != null
  }

  override fun getComponent() = component

  override fun getContentManager(): ContentManager {
    ensureContentInitialized()
    return contentManager
  }

  // to avoid ensureContentInitialized call - myContentManager can report canCloseContents without full initialization
  fun canCloseContents() = contentManager.canCloseContents()

  override fun getIcon(): Icon? {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return icon
  }

  override fun getTitle(): String? {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return contentManager.selectedContent?.displayName
  }

  override fun getStripeTitle(): String {
    ApplicationManager.getApplication().assertIsDispatchThread()
    return ObjectUtils.notNull(stripeTitle, id)
  }

  override fun setIcon(icon: Icon) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val oldIcon = getIcon()
    if (EventLog.LOG_TOOL_WINDOW_ID != id) {
      if (oldIcon !== icon &&
          icon !is LayeredIcon &&
          (abs(icon.iconHeight - JBUIScale.scale(13f)) >= 1 || abs(icon.iconWidth - JBUIScale.scale(13f)) >= 1)) {
        LOG.warn("ToolWindow icons should be 13x13. Please fix ToolWindow (ID:  $id) or icon $icon")
      }
    }
    this.icon = ToolWindowIcon(icon, id)
    toolWindowManager.toolWindowPropertyChanged(this, ToolWindowEx.PROP_ICON)
  }

  override fun setTitle(title: String) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val selected = contentManager.selectedContent
    if (selected != null) {
      selected.displayName = title
    }
    toolWindowManager.toolWindowPropertyChanged(this, ToolWindowEx.PROP_TITLE)
  }

  override fun setStripeTitle(value: String) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    stripeTitle = value
    toolWindowManager.toolWindowPropertyChanged(this, ToolWindowEx.PROP_STRIPE_TITLE)
  }

  fun setDecorator(value: InternalDecorator) {
    decorator = value
  }

  fun fireActivated() {
    decorator?.fireActivated()
  }

  fun fireHidden() {
    decorator?.fireHidden()
  }

  fun fireHiddenSide() {
    decorator?.fireHiddenSide()
  }

  val popupGroup: ActionGroup?
    get() = decorator?.createPopupGroup()

  override fun setDefaultState(anchor: ToolWindowAnchor?, type: ToolWindowType?, floatingBounds: Rectangle?) {
    toolWindowManager.setDefaultState(this, anchor, type, floatingBounds)
  }

  override fun setToHideOnEmptyContent(value: Boolean) {
    hideOnEmptyContent = value
  }

  override fun isToHideOnEmptyContent() = hideOnEmptyContent

  override fun setShowStripeButton(show: Boolean) {
    toolWindowManager.setShowStripeButton(id, show)
  }

  override fun isShowStripeButton() = toolWindowManager.isShowStripeButton(id)

  override fun isDisposed() = contentManager.isDisposed

  fun setContentFactory(value: ToolWindowFactory) {
    contentFactory = value
    value.init(this)
  }

  fun ensureContentInitialized() {
    val currentContentFactory = contentFactory
    if (currentContentFactory != null) {
      // clear it first to avoid SOE
      this.contentFactory = null
      contentManager.removeAllContents(false)
      currentContentFactory.createToolWindowContent(toolWindowManager.project, this)
    }
  }

  override fun getHelpId() = helpId

  override fun setHelpId(value: String) {
    helpId = value
  }

  override fun showContentPopup(inputEvent: InputEvent) {
    contentUi.toggleContentPopup()
  }
}