// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.execution.ui.layout.LayoutAttractionPolicy
import com.intellij.execution.ui.layout.LayoutStateDefaults
import com.intellij.execution.ui.layout.LayoutViewOptions
import com.intellij.execution.ui.layout.PlaceInGrid
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl
import com.intellij.execution.ui.layout.impl.ViewImpl
import com.intellij.ide.rpc.setupTransfer
import com.intellij.ide.ui.icons.rpcIdOrNull
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentWithActions
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.platform.debugger.impl.rpc.XDebugTabLayouterEvent
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel

internal class RunnerLayoutUiBridge(
  project: Project,
  private val disposable: Disposable,
) : RunnerLayoutUi, LayoutStateDefaults, LayoutViewOptions {
  private val contentManager: ContentManager =
    ContentFactory.getInstance().createContentManager(true, project)
  private val eventsChannel = Channel<XDebugTabLayouterEvent>(Channel.UNLIMITED)
  private val contents = hashMapOf<Content, Int>()
  private val contentsByUniqueId = hashMapOf<Int, Content>()

  init {
    Disposer.register(disposable, contentManager)
  }

  val events: Flow<XDebugTabLayouterEvent> = channelFlow {
    eventsChannel.consumeEach { send(it) }
  }

  override fun createContent(contentId: @NonNls String, component: JComponent, displayName: @Nls String, icon: Icon?, toFocus: JComponent?): Content {
    // Do not pass component to the fake content, as it will break LUX transfer due to adding content into a UI hierarchy
    val content = ContentFactory.getInstance().createContent(JLabel(), displayName, false)
    content.putUserData(RunnerLayoutUiImpl.CONTENT_TYPE, contentId)
    content.putUserData(ViewImpl.ID, contentId)
    content.icon = icon
    if (toFocus != null) {
      content.preferredFocusableComponent = toFocus
    }
    sendContentCreationEvent(component, content, contentId, displayName, icon)
    return content
  }

  override fun createContent(contentId: @NonNls String, contentWithActions: ComponentWithActions, displayName: @Nls String, icon: Icon?, toFocus: JComponent?): Content {
    return createContent(contentId, contentWithActions.component, displayName, icon, toFocus)
  }

  private fun sendContentCreationEvent(component: JComponent, fakeContent: Content, contentId: @NonNls String, displayName: @Nls String, icon: Icon?) {
    val edtDisposable = Disposer.newDisposable()
    Disposer.register(disposable) {
      UIUtil.invokeLaterIfNeeded {
        Disposer.dispose(edtDisposable)
      }
    }
    val tabId = component.setupTransfer(edtDisposable)
    val uniqueId = contents.size
    contents[fakeContent] = uniqueId
    contentsByUniqueId[uniqueId] = fakeContent
    eventsChannel.trySend(XDebugTabLayouterEvent.ContentCreated(uniqueId, contentId, tabId, displayName, icon?.rpcIdOrNull()))
  }

  override fun addContent(content: Content): Content {
    val uniqueId = contents[content]
    if (uniqueId != null) {
      eventsChannel.trySend(XDebugTabLayouterEvent.TabAdded(uniqueId, content.isCloseable))
    }
    contentManager.addContent(content)
    return content
  }

  override fun addContent(content: Content, defaultTabId: Int, defaultPlace: PlaceInGrid, defaultIsMinimized: Boolean): Content {
    val uniqueId = contents[content]
    if (uniqueId != null) {
      eventsChannel.trySend(
        XDebugTabLayouterEvent.TabAddedExtended(uniqueId, defaultTabId, defaultPlace,
                                                defaultIsMinimized, content.isCloseable)
      )
    }
    contentManager.addContent(content)
    contentManager.removeFromSelection(content)
    return content
  }

  override fun removeContent(content: Content?, dispose: Boolean): Boolean {
    val uniqueId = contents.remove(content)
    if (uniqueId != null) {
      contentsByUniqueId.remove(uniqueId)
      eventsChannel.trySend(XDebugTabLayouterEvent.TabRemoved(uniqueId))
    }
    return content != null && contentManager.removeContent(content, dispose)
  }

  fun setSelection(contentUniqueId: Int, isSelected: Boolean) {
    val content = contentsByUniqueId[contentUniqueId] ?: return
    if (isSelected) {
      contentManager.setSelectedContent(content)
    }
    else {
      contentManager.removeFromSelection(content)
    }
  }

  // --- RunnerLayoutUi ---

  override fun getDefaults(): LayoutStateDefaults = this
  override fun getOptions(): LayoutViewOptions = this
  override fun getContentManager(): ContentManager = contentManager
  override fun getComponent(): JComponent = contentManager.component
  override fun isDisposed(): Boolean = contentManager.isDisposed

  override fun findContent(contentId: String): Content? {
    for (i in 0 until contentManager.contentCount) {
      val c = contentManager.getContent(i) ?: continue
      if (contentId == c.getUserData(RunnerLayoutUiImpl.CONTENT_TYPE)) return c
    }
    return null
  }

  override fun selectAndFocus(content: Content?, requestFocus: Boolean, forced: Boolean): ActionCallback {
    return selectAndFocus(content, requestFocus, forced, false)
  }

  override fun selectAndFocus(content: Content?, requestFocus: Boolean, forced: Boolean, implicit: Boolean): ActionCallback {
    if (content == null) return ActionCallback.REJECTED
    return contentManager.setSelectedContent(content, requestFocus, forced, implicit)
  }

  override fun addListener(listener: ContentManagerListener, parent: Disposable): RunnerLayoutUi {
    contentManager.addContentManagerListener(listener)
    Disposer.register(parent) { contentManager.removeContentManagerListener(listener) }
    return this
  }

  override fun removeListener(listener: ContentManagerListener) {
    contentManager.removeContentManagerListener(listener)
  }

  override fun getContents(): Array<Content> {
    return Array(contentManager.contentCount) { contentManager.getContent(it)!! }
  }

  override fun attractBy(condition: String) {}
  override fun clearAttractionBy(condition: String) {}
  override fun setBouncing(content: Content, activate: Boolean) {}
  override fun updateActionsNow() {}

  // --- LayoutStateDefaults ---

  override fun initTabDefaults(id: Int, text: String?, icon: Icon?): LayoutStateDefaults = this
  override fun initContentAttraction(contentId: String, condition: String, policy: LayoutAttractionPolicy): LayoutStateDefaults = this
  override fun cancelContentAttraction(condition: String): LayoutStateDefaults = this

  // --- LayoutViewOptions ---

  override fun setTopLeftToolbar(actions: ActionGroup, place: String): LayoutViewOptions = this
  override fun setTopMiddleToolbar(actions: ActionGroup, place: String): LayoutViewOptions = this
  override fun setTopRightToolbar(actions: ActionGroup, place: String): LayoutViewOptions = this
  override fun setLeftToolbar(leftToolbar: ActionGroup, place: String): LayoutViewOptions = this
  override fun setMinimizeActionEnabled(enabled: Boolean): LayoutViewOptions = this
  override fun setMoveToGridActionEnabled(enabled: Boolean): LayoutViewOptions = this
  override fun setAttractionPolicy(contentId: String, policy: LayoutAttractionPolicy): LayoutViewOptions = this
  override fun setConditionAttractionPolicy(condition: String, policy: LayoutAttractionPolicy): LayoutViewOptions = this
  override fun isToFocus(content: Content, condition: String): Boolean = false
  override fun setToFocus(content: Content?, condition: String): LayoutViewOptions = this
  override fun getLayoutActions(): AnAction = DefaultActionGroup()
  override fun getLayoutActionsList(): Array<AnAction> = AnAction.EMPTY_ARRAY
  override fun setTabPopupActions(group: ActionGroup): LayoutViewOptions = this
  override fun setAdditionalFocusActions(group: ActionGroup): LayoutViewOptions = this
  override fun getSettingsActions(): AnAction = DefaultActionGroup()
  override fun getSettingsActionsList(): Array<AnAction> = AnAction.EMPTY_ARRAY
}
