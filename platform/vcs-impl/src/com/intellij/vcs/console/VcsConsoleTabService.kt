// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.console

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConsoleLine
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
interface VcsConsoleTabService {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): VcsConsoleTabService = project.service()
  }

  @CalledInAny
  fun addMessage(message: @Nls String?, contentType: ConsoleViewContentType)

  @CalledInAny
  fun addMessage(line: VcsConsoleLine?)

  @RequiresEdt
  fun isConsoleVisible(): Boolean

  @CalledInAny
  fun isConsoleEmpty(): Boolean

  @CalledInAny
  fun hadMessages(): Boolean

  @CalledInAny
  fun showConsoleTabAndScrollToTheEnd()
}

@ApiStatus.Internal
class MockVcsConsoleTabService : VcsConsoleTabService {
  @CalledInAny
  override fun addMessage(message: @Nls String?, contentType: ConsoleViewContentType) {
  }

  @CalledInAny
  override fun addMessage(line: VcsConsoleLine?) {
  }

  @RequiresEdt
  override fun isConsoleVisible(): Boolean {
    return false
  }

  @CalledInAny
  override fun isConsoleEmpty(): Boolean {
    return true
  }

  @CalledInAny
  override fun hadMessages(): Boolean {
    return false
  }

  @CalledInAny
  override fun showConsoleTabAndScrollToTheEnd() {
  }
}

/**
 * Maximum number of messages that are kept until the console view is created.
 * The oldest messages are dropped on overflow, as the console itself does with its cycle buffer.
 */
private const val MAX_PENDING_LINES = 1000

internal class VcsConsoleTabServiceImpl(
  private val project: Project,
  private val cs: CoroutineScope,
) : VcsConsoleTabService {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): VcsConsoleTabService = project.service()
  }

  private val showConsoleSignal = MutableSharedFlow<ShowConsoleSignal>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val pendingMessages = MutableSharedFlow<VcsConsoleLine>(replay = MAX_PENDING_LINES, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    cs.launch(Dispatchers.UiWithModelAccess) {
      showConsoleSignal.collect {
        showConsoleTab(it.focusLatest)
      }
    }
  }

  @CalledInAny
  override fun addMessage(message: @Nls String?, contentType: ConsoleViewContentType) {
    addMessage(VcsConsoleLine.create(message, contentType))
  }

  @CalledInAny
  override fun addMessage(line: VcsConsoleLine?) {
    if (line == null) return
    if (project.isDisposed || project.isDefault) return

    pendingMessages.tryEmit(line)
    showConsoleSignal.tryEmit(ShowConsoleSignal(focusLatest = false))
  }

  @RequiresEdt
  override fun isConsoleVisible(): Boolean {
    if (project.isDisposed || project.isDefault) return false

    val toolWindow = ChangesViewContentManager.getToolWindowFor(project, ChangesViewContentManager.CONSOLE) ?: return false
    val contentManager = toolWindow.contentManagerIfCreated ?: return false
    val content = contentManager.findContent(ChangesViewContentManager.CONSOLE) ?: return false
    return contentManager.isSelected(content) && content.component.isShowing
  }

  @CalledInAny
  override fun isConsoleEmpty(): Boolean {
    if (project.isDisposed || project.isDefault) return true

    return pendingMessages.replayCache.isEmpty()
  }

  @CalledInAny
  override fun hadMessages(): Boolean = pendingMessages.replayCache.isNotEmpty()

  @RequiresEdt
  private fun showConsoleTab(selectContent: Boolean) {
    if (project.isDisposed || project.isDefault) return

    val contentTab = ChangesViewContentManager.getInstance(project).findContent(ChangesViewContentManager.CONSOLE)
    if (contentTab == null) {
      createConsoleContentTab()
    }

    if (selectContent) {
      ChangesViewContentManager.getInstance(project).selectContent(ChangesViewContentManager.CONSOLE)
      ChangesViewContentManager.getToolWindowFor(project, ChangesViewContentManager.CONSOLE)?.activate(null)
    }
  }

  @CalledInAny
  override fun showConsoleTabAndScrollToTheEnd() {
    showConsoleSignal.tryEmit(ShowConsoleSignal(focusLatest = true))
  }

  @RequiresEdt
  private fun createConsoleContentTab() {
    val consoleView = getOrCreateConsoleView()

    val panel = SimpleToolWindowPanel(false, true)
    panel.setContent(consoleView.component)

    val actionGroup = DefaultActionGroup(*consoleView.createConsoleActions())
    val toolbar = ActionManager.getInstance().createActionToolbar("VcsManager", actionGroup, false)
    toolbar.targetComponent = consoleView.component
    panel.toolbar = toolbar.component

    val contentTab = ContentImpl(panel, VcsBundle.message("vcs.console.toolwindow.display.name"), true)
    contentTab.setPreferredFocusedComponent { consoleView.preferredFocusableComponent }

    contentTab.tabName = ChangesViewContentManager.CONSOLE //NON-NLS
    contentTab.putUserData(ChangesViewContentManager.ORDER_WEIGHT_KEY,
                           ChangesViewContentManager.TabOrderWeight.CONSOLE.weight)
    contentTab.setDisposer(consoleView)

    ChangesViewContentManager.getInstance(project).addContent(contentTab)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @RequiresEdt
  private fun getOrCreateConsoleView(): VcsConsoleView {
    val view = VcsConsoleView(project) {
      pendingMessages.resetReplayCache()
    }

    cs.launch(Dispatchers.UiWithModelAccess + CoroutineName("VCS console printer")) {
      pendingMessages.collect {
        it.print(view)
      }
    }.cancelOnDispose(view)

    cs.launch(Dispatchers.UiWithModelAccess + CoroutineName("VCS console scroller")) {
      showConsoleSignal.filter { it.focusLatest }.collect {
        view.requestScrollingToEnd()
      }
    }.cancelOnDispose(view)

    return view
  }
}

/**
 * @param focusLatest - select the tab and show the latest shown lines
 */
private data class ShowConsoleSignal(val focusLatest: Boolean)
