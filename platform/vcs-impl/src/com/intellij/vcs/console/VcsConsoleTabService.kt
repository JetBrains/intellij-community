// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.console

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConsoleLine
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.util.concurrency.annotations.RequiresEdt
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

  @RequiresEdt
  fun isConsoleEmpty(): Boolean

  @CalledInAny
  fun hadMessages(): Boolean

  @RequiresEdt
  fun showConsoleTab(selectContent: Boolean, onShown: Runnable?)

  @RequiresEdt
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

  @RequiresEdt
  override fun isConsoleEmpty(): Boolean {
    return true
  }

  override fun hadMessages(): Boolean {
    return false
  }

  @RequiresEdt
  override fun showConsoleTab(selectContent: Boolean, onShown: Runnable?) {
  }

  @RequiresEdt
  override fun showConsoleTabAndScrollToTheEnd() {
  }
}

internal class VcsConsoleTabServiceImpl(val project: Project) : VcsConsoleTabService, Disposable {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): VcsConsoleTabService = project.service()
  }

  private var hadMessages: Boolean = false

  private val consoleView: VcsConsoleView = VcsConsoleView(project)

  override fun dispose() {
    Disposer.dispose(consoleView)
  }


  @CalledInAny
  override fun addMessage(message: @Nls String?, contentType: ConsoleViewContentType) {
    addMessage(VcsConsoleLine.create(message, contentType))
  }

  @CalledInAny
  override fun addMessage(line: VcsConsoleLine?) {
    if (line == null) return
    if (project.isDisposed || project.isDefault) return

    line.print(consoleView)
    hadMessages = true

    if (Registry.`is`("vcs.showConsole")) {
      runInEdt(ModalityState.nonModal()) {
        showConsoleTab(false, null)
      }
    }
  }

  @RequiresEdt
  override fun isConsoleVisible(): Boolean {
    if (project.isDisposed || project.isDefault) return false

    val toolWindow = ChangesViewContentManager.getToolWindowFor(project, ChangesViewContentManager.CONSOLE) ?: return false
    val contentManager = toolWindow.contentManagerIfCreated ?: return false
    return contentManager.getContent(consoleView.component) != null
  }

  @RequiresEdt
  override fun isConsoleEmpty(): Boolean {
    if (project.isDisposed || project.isDefault) return true
    return consoleView.contentSize == 0
  }

  @CalledInAny
  override fun hadMessages(): Boolean = hadMessages

  @RequiresEdt
  override fun showConsoleTab(selectContent: Boolean, onShown: Runnable?) {
    if (project.isDisposed || project.isDefault) return

    val contentTab = ChangesViewContentManager.getInstance(project).findContent(ChangesViewContentManager.CONSOLE)
    if (contentTab == null) {
      createConsoleContentTab()
    }

    if (selectContent) {
      ChangesViewContentManager.getInstance(project).selectContent(ChangesViewContentManager.CONSOLE)
      ChangesViewContentManager.getToolWindowFor(project, ChangesViewContentManager.CONSOLE)?.activate(onShown)
    }
  }

  @RequiresEdt
  override fun showConsoleTabAndScrollToTheEnd() {
    showConsoleTab(true) {
      consoleView.requestScrollingToEnd()
    }
  }

  private fun createConsoleContentTab(): Content {
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

    ChangesViewContentManager.getInstance(project).addContent(contentTab)

    return contentTab
  }
}
