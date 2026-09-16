// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.statistics.devkit.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.NonEmptyActionGroup
import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.platform.statistics.devkit.StatisticsDevKitUtil.DEFAULT_RECORDER
import com.intellij.platform.statistics.devkit.StatisticsDevKitUtil.getLogProvidersInTestMode
import com.intellij.platform.statistics.devkit.actions.RecordStateStatisticsEventLogAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.statistics.devkit.icons.PlatformStatisticsDevkitIcons
import com.intellij.ui.content.ContentFactory
import javax.swing.Icon

/**
 * Creates a toolwindow with feature usage statistics event log
 *
 * The backend instance is intentionally projected into JetBrains Client.
 */
@Suppress("SplitModeApiUsage")
internal open class StatisticsEventLogToolWindowFactory : ToolWindowFactory, DumbAware {
  override fun init(toolWindow: ToolWindow) {
    // Match the standard "(On Host)" suffix used for duplicated actions.
    @Suppress("DialogTitleCapitalization")
    val title = if (IdeProductMode.isBackend) {
      StatisticsBundle.message("stats.event.log.toolwindow.on.host")
    }
    else {
      IdeBundle.message("toolwindow.stripe.Statistics_Event_Log")
    }
    toolWindow.title = title
    toolWindow.stripeTitle = title
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val recorders = StatisticsRecorderUtil.getRecordersInTestMode()
    if (recorders.isEmpty()) return

    val mainTab = if (recorders.contains(DEFAULT_RECORDER)) DEFAULT_RECORDER else recorders[0]
    createNewTab(project, toolWindow, mainTab)
    toolWindow.setToHideOnEmptyContent(true)
    if (toolWindow is ToolWindowEx) {
      val newSessionActionGroup = createNewSessionActionGroup(project, toolWindow.id)
      toolWindow.setTabActions(newSessionActionGroup)
    }

    val toolWindowId = toolWindow.id
    project.messageBus.connect().subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      override fun toolWindowShown(toolWindow: ToolWindow) {
        if (toolWindowId == toolWindow.id && toolWindow.isVisible && toolWindow.contentManager.contentCount == 0) {
          // open a new session if all tabs were closed manually
          createNewTab(project, toolWindow, mainTab)
        }
      }
    })
  }

  override val icon: Icon
    get() = PlatformStatisticsDevkitIcons.StatisticsEventLog

  override suspend fun isApplicableAsync(project: Project) = StatisticsRecorderUtil.isAnyTestModeEnabled()
}

internal class FrontendStatisticsEventLogToolWindowFactory : StatisticsEventLogToolWindowFactory() {
  override suspend fun isApplicableAsync(project: Project): Boolean {
    // The backend registration supplies the tool window in monolith.
    return IdeProductMode.isFrontend && super.isApplicableAsync(project)
  }
}

private fun createNewSessionActionGroup(project: Project, toolWindowId: String): NonEmptyActionGroup {
  val actionGroup = NonEmptyActionGroup()
  actionGroup.isPopup = true
  actionGroup.templatePresentation.icon = AllIcons.General.Add

  val actions = getLogProvidersInTestMode().map { logger ->
    val recorder = logger.recorderId
    CreateNewSessionAction(project, recorder, toolWindowId)
  }
  actionGroup.addAll(actions)
  return actionGroup
}

internal class CreateNewSessionAction(
  private val project: Project,
  private val recorderId: String,
  private val toolWindowId: String,
) : AnAction(recorderId) {
  override fun actionPerformed(e: AnActionEvent) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId) ?: return
    createNewTab(project, toolWindow, recorderId)
  }
}

private fun createNewTab(project: Project, toolWindow: ToolWindow, recorderId: String) {
  val eventLogToolWindow = StatisticsEventLogToolWindow(project, recorderId, toolWindow.id)
  val content = ContentFactory.getInstance().createContent(eventLogToolWindow.component, recorderId, true)
  content.preferredFocusableComponent = eventLogToolWindow.component
  content.toolwindowTitle = recorderId
  toolWindow.contentManager.addContent(content)
  toolWindow.contentManager.setSelectedContent(content)
  RecordStateStatisticsEventLogAction.checkLogRecordingEnabled(project, recorderId)
}