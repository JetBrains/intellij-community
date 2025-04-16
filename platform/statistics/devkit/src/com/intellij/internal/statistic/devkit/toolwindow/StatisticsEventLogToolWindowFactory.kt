// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.NonEmptyActionGroup
import com.intellij.internal.statistic.devkit.PlatformStatisticsDevkitIcons
import com.intellij.internal.statistic.devkit.StatisticsDevKitUtil.DEFAULT_RECORDER
import com.intellij.internal.statistic.devkit.StatisticsDevKitUtil.getLogProvidersInTestMode
import com.intellij.internal.statistic.devkit.actions.RecordStateStatisticsEventLogAction
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.ContentFactory
import javax.swing.Icon

/**
 * Creates a toolwindow with feature usage statistics event log
 */
private class StatisticsEventLogToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun init(toolWindow: ToolWindow) {
    toolWindow.title = IdeBundle.message("toolwindow.stripe.Statistics_Event_Log")
    toolWindow.stripeTitle = IdeBundle.message("toolwindow.stripe.Statistics_Event_Log")
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val recorders = StatisticsRecorderUtil.getRecordersInTestMode()
    if (recorders.isEmpty()) return

    val mainTab = if (recorders.contains(DEFAULT_RECORDER)) DEFAULT_RECORDER else recorders[0]
    createNewTab(project, toolWindow, mainTab)
    toolWindow.setToHideOnEmptyContent(true)
    if (toolWindow is ToolWindowEx) {
      val newSessionActionGroup = createNewSessionActionGroup(project)
      toolWindow.setTabActions(newSessionActionGroup)
    }

    project.messageBus.connect().subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun toolWindowShown(toolWindow: ToolWindow) {
        if (eventLogToolWindowsId == toolWindow.id && toolWindow.isVisible && toolWindow.contentManager.contentCount == 0) {
          // open a new session if all tabs were closed manually
          createNewTab(project, toolWindow, mainTab)
        }
      }
    })
  }

  override val icon: Icon
    get() =  PlatformStatisticsDevkitIcons.StatisticsEventLog

  override suspend fun isApplicableAsync(project: Project) = StatisticsRecorderUtil.isAnyTestModeEnabled()
}

private fun createNewSessionActionGroup(project: Project): NonEmptyActionGroup {
  val actionGroup = NonEmptyActionGroup()
  actionGroup.isPopup = true
  actionGroup.templatePresentation.icon = AllIcons.General.Add

  val actions = getLogProvidersInTestMode().map { logger ->
    val recorder = logger.recorderId
    CreateNewSessionAction(project, recorder)
  }
  actionGroup.addAll(actions)
  return actionGroup
}

private class CreateNewSessionAction(private val project: Project, private val recorderId: String) : AnAction(recorderId) {
  override fun actionPerformed(e: AnActionEvent) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(eventLogToolWindowsId) ?: return
    createNewTab(project, toolWindow, recorderId)
  }
}

private fun createNewTab(project: Project, toolWindow: ToolWindow, recorderId: String) {
  val eventLogToolWindow = StatisticsEventLogToolWindow(project, recorderId)
  val content = ContentFactory.getInstance().createContent(eventLogToolWindow.component, recorderId, true)
  content.preferredFocusableComponent = eventLogToolWindow.component
  content.toolwindowTitle = recorderId
  toolWindow.contentManager.addContent(content)
  toolWindow.contentManager.setSelectedContent(content)
  RecordStateStatisticsEventLogAction.checkLogRecordingEnabled(project, recorderId)
}