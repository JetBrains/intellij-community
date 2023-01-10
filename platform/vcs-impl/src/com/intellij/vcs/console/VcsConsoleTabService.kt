// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.console

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConsoleLine
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.Nls

@Service(Service.Level.PROJECT)
class VcsConsoleTabService(val project: Project) : Disposable {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): VcsConsoleTabService = project.service()
  }

  private val consoleView: VcsConsoleView = VcsConsoleView(project)

  override fun dispose() {
    Disposer.dispose(consoleView)
  }


  @CalledInAny
  fun addMessage(message: @Nls String?, contentType: ConsoleViewContentType) {
    addMessage(VcsConsoleLine.create(message, contentType))
  }

  @CalledInAny
  fun addMessage(line: VcsConsoleLine?) {
    if (line == null) return
    if (project.isDisposed || project.isDefault) return

    line.print(consoleView)

    if (Registry.`is`("vcs.showConsole")) {
      runInEdt(ModalityState.NON_MODAL) {
        showConsoleTab(false, null)
      }
    }
  }

  @RequiresEdt
  fun isConsoleVisible(): Boolean {
    if (project.isDisposed || project.isDefault) return false

    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID) ?: return false
    val contentManager = toolWindow.contentManagerIfCreated ?: return false
    return contentManager.getContent(consoleView.component) != null
  }

  @RequiresEdt
  fun isConsoleEmpty(): Boolean {
    if (project.isDisposed || project.isDefault) return true
    return consoleView.contentSize == 0
  }

  @RequiresEdt
  fun showConsoleTab(selectContent: Boolean, onShown: Runnable?) {
    if (project.isDisposed || project.isDefault) return

    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID) ?: return
    val contentManager = toolWindow.contentManager

    val contentTab: Content = contentManager.getContent(consoleView.component)
                              ?: contentManager.createConsoleContentTab()

    if (selectContent) {
      contentManager.setSelectedContent(contentTab, true)
      toolWindow.show(onShown)
    }
  }

  @RequiresEdt
  fun showConsoleTabAndScrollToTheEnd() {
    showConsoleTab(true) {
      consoleView.requestScrollingToEnd()
    }
  }

  private fun ContentManager.createConsoleContentTab(): Content {
    val actionGroup = DefaultActionGroup(*consoleView.createConsoleActions())
    val toolbar = ActionManager.getInstance().createActionToolbar("VcsManager", actionGroup, false)
    toolbar.targetComponent = consoleView.component

    val panel = SimpleToolWindowPanel(false, true)
    panel.setContent(consoleView.component)
    panel.toolbar = toolbar.component

    val contentTab = ContentImpl(panel, VcsBundle.message("vcs.console.toolwindow.display.name"), true)
    contentTab.setPreferredFocusedComponent { consoleView.preferredFocusableComponent }

    addContent(contentTab)

    return contentTab
  }
}
