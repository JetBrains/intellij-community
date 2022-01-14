// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.TabDescriptor
import com.intellij.ui.content.TabGroupId
import com.intellij.util.Consumer
import com.intellij.util.ContentUtilEx
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogUi
import com.intellij.vcs.log.impl.VcsLogManager.VcsLogUiFactory
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogPanel
import com.intellij.vcs.log.ui.VcsLogUiEx
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function
import java.util.function.Supplier
import javax.swing.JComponent

/**
 * Utility methods to operate VCS Log tabs as [Content]s of the [ContentManager] of the VCS toolwindow.
 */
object VcsLogContentUtil {

  private fun getLogUi(c: JComponent): VcsLogUiEx? {
    val uis = VcsLogPanel.getLogUis(c)
    require(uis.size <= 1) { "Component $c has more than one log ui: $uis" }
    return uis.singleOrNull()
  }

  internal fun selectLogUi(project: Project, logUi: VcsLogUi): Boolean {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID) ?: return false
    val manager = toolWindow.contentManager
    val component = ContentUtilEx.findContentComponent(manager) { c -> getLogUi(c)?.id == logUi.id } ?: return false

    if (!toolWindow.isVisible) {
      toolWindow.activate(null)
    }
    return ContentUtilEx.selectContent(manager, component, true)
  }

  fun getId(content: Content): String? {
    return getLogUi(content.component)?.id
  }

  @JvmStatic
  fun <U : VcsLogUiEx> openLogTab(project: Project,
                                  logManager: VcsLogManager,
                                  tabGroupId: TabGroupId,
                                  tabDisplayName: Function<U, @NlsContexts.TabTitle String>,
                                  factory: VcsLogUiFactory<out U>,
                                  focus: Boolean): U {
    val logUi = logManager.createLogUi(factory, VcsLogTabLocation.TOOL_WINDOW)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID)
                     ?: throw IllegalStateException("Could not find tool window for id ${ChangesViewContentManager.TOOLWINDOW_ID}")
    ContentUtilEx.addTabbedContent(toolWindow.contentManager, tabGroupId,
                                   TabDescriptor(VcsLogPanel(logManager, logUi), Supplier { tabDisplayName.apply(logUi) }, logUi), focus)
    if (focus) {
      toolWindow.activate(null)
    }
    return logUi
  }

  fun closeLogTab(manager: ContentManager, tabId: String): Boolean {
    return ContentUtilEx.closeContentTab(manager) { c: JComponent ->
      getLogUi(c)?.id == tabId
    }
  }

  @JvmStatic
  fun runInMainLog(project: Project, consumer: Consumer<in MainVcsLogUi>) {
    val window = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID)
    if (window == null || !selectMainLog(window.contentManager)) {
      showLogIsNotAvailableMessage(project)
      return
    }

    val runConsumer = Runnable { VcsLogContentProvider.getInstance(project)!!.executeOnMainUiCreated(consumer) }
    if (!window.isVisible) {
      window.activate(runConsumer)
    }
    else {
      runConsumer.run()
    }
  }

  @RequiresEdt
  fun showLogIsNotAvailableMessage(project: Project) {
    VcsBalloonProblemNotifier.showOverChangesView(project, VcsLogBundle.message("vcs.log.is.not.available"), MessageType.WARNING)
  }

  internal fun findMainLog(cm: ContentManager): Content? {
    // here tab name is used instead of log ui id to select the correct tab
    // it's done this way since main log ui may not be created when this method is called
    return cm.contents.find { VcsLogContentProvider.TAB_NAME == it.tabName }
  }

  internal fun selectMainLog(cm: ContentManager): Boolean {
    val mainContent = findMainLog(cm) ?: return false
    cm.setSelectedContent(mainContent)
    return true
  }

  fun selectMainLog(project: Project): Boolean {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID) ?: return false
    return selectMainLog(toolWindow.contentManager)
  }

  @JvmStatic
  fun updateLogUiName(project: Project, ui: VcsLogUi) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID) ?: return
    val manager = toolWindow.contentManager
    val component = ContentUtilEx.findContentComponent(manager) { c: JComponent -> ui === getLogUi(c) } ?: return
    ContentUtilEx.updateTabbedContentDisplayName(manager, component)
  }

  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  @Deprecated("use VcsProjectLog#runWhenLogIsReady(Project, Consumer) instead.")
  @JvmStatic
  @RequiresBackgroundThread
  fun getOrCreateLog(project: Project): VcsLogManager? {
    VcsProjectLog.ensureLogCreated(project)
    return VcsProjectLog.getInstance(project).logManager
  }
}