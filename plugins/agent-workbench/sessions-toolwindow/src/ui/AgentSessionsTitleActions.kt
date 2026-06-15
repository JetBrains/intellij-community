// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

// @spec community/plugins/agent-workbench/spec/sessions/agent-sessions-tree.spec.md

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.statusColor
import com.intellij.agent.workbench.sessions.AgentSessionsBundle
import com.intellij.agent.workbench.sessions.core.providers.agentSessionThreadStatusIcon
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.model.AgentSessionArchivedRangePreset
import com.intellij.agent.workbench.sessions.model.AgentSessionThreadViewMode
import com.intellij.agent.workbench.sessions.service.AgentSessionLaunchService
import com.intellij.agent.workbench.sessions.state.AgentSessionThreadViewStateService
import com.intellij.agent.workbench.sessions.toolwindow.tree.formatRelativeTimeShort
import com.intellij.agent.workbench.sessions.tree.threadDisplayTitle
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

private const val MAX_POPUP_ROW_TITLE_LENGTH: Int = 60
private val ACTIVITY_COUNTER_LOG = logger<AgentSessionsActivityCounterAction>()

internal fun createAgentSessionsTitleActions(): List<AnAction> {
  return listOf(
    AgentSessionsActivityCounterAction(AgentSessionsActivityBucket.ATTENTION),
    AgentSessionsActivityCounterAction(AgentSessionsActivityBucket.RUNNING),
    AgentSessionsActivityCounterAction(AgentSessionsActivityBucket.DONE),
    AgentSessionsShowActiveThreadsHeaderAction(),
    AgentSessionsArchivedContextHeaderAction(),
    AgentSessionsArchivedRangeHeaderAction(),
  )
}

internal class AgentSessionsShowActiveThreadsHeaderAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val state = service<AgentSessionThreadViewStateService>().state.value
    val presentation = e.presentation
    if (state.mode != AgentSessionThreadViewMode.ARCHIVED) {
      presentation.setEnabledAndVisible(false)
      return
    }

    presentation.icon = AllIcons.Actions.Back
    presentation.text = AgentSessionsBundle.message("toolwindow.action.show.active.threads")
    presentation.description = AgentSessionsBundle.message("toolwindow.action.show.active.threads.description")
    presentation.setEnabledAndVisible(true)
  }

  override fun actionPerformed(e: AnActionEvent) {
    service<AgentSessionThreadViewStateService>().setMode(AgentSessionThreadViewMode.ACTIVE)
  }
}

internal class AgentSessionsArchivedContextHeaderAction : DumbAwareAction(), CustomComponentAction {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val state = service<AgentSessionThreadViewStateService>().state.value
    val presentation = e.presentation
    if (state.mode != AgentSessionThreadViewMode.ARCHIVED) {
      presentation.setEnabledAndVisible(false)
      return
    }

    presentation.text = AgentSessionsBundle.message("toolwindow.context.archived")
    presentation.description = AgentSessionsBundle.message("toolwindow.threadview.header.archived.tooltip")
    presentation.setEnabledAndVisible(true)
  }

  override fun actionPerformed(e: AnActionEvent) {
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val component = AgentSessionsArchivedContextHeaderComponent(action = null, showChevron = false, bold = true)
    component.update(presentation)
    return component
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    (component as? AgentSessionsArchivedContextHeaderComponent)?.update(presentation)
  }
}

internal class AgentSessionsArchivedRangeHeaderAction : DumbAwareAction(), CustomComponentAction {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val state = service<AgentSessionThreadViewStateService>().state.value
    val presentation = e.presentation
    if (state.mode != AgentSessionThreadViewMode.ARCHIVED) {
      presentation.setEnabledAndVisible(false)
      return
    }

    val rangeLabel = archivedRangePresetLabel(state.archivedRangePreset)
    presentation.text = rangeLabel
    presentation.description = AgentSessionsBundle.message("toolwindow.threadview.header.archived.range.tooltip", rangeLabel)
    presentation.setEnabledAndVisible(true)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val component = e.inputEvent?.component as? JComponent ?: return
    val viewStateService = service<AgentSessionThreadViewStateService>()
    JBPopupFactory.getInstance()
      .createActionGroupPopup(
        null,
        createArchivedRangeHeaderPopupGroup(viewStateService),
        DataManager.getInstance().getDataContext(component),
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        true,
      )
      .showUnderneathOf(component)
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val component = AgentSessionsArchivedContextHeaderComponent(action = this, showChevron = true, bold = false)
    component.update(presentation)
    return component
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    (component as? AgentSessionsArchivedContextHeaderComponent)?.update(presentation)
  }
}

internal fun createArchivedRangeHeaderPopupGroup(viewStateService: AgentSessionThreadViewStateService): DefaultActionGroup {
  return DefaultActionGroup().apply {
    AgentSessionArchivedRangePreset.entries.forEach { preset ->
      add(ArchivedRangePresetToggleAction(preset, viewStateService))
    }
  }
}

private class ArchivedRangePresetToggleAction(
  private val preset: AgentSessionArchivedRangePreset,
  private val viewStateService: AgentSessionThreadViewStateService,
) : ToggleAction(archivedRangePresetLabel(preset)), com.intellij.openapi.project.DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = viewStateService.state.value.mode == AgentSessionThreadViewMode.ARCHIVED
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return viewStateService.state.value.archivedRangePreset == preset
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    if (!state) return
    viewStateService.setArchivedRangePreset(preset)
  }
}

private fun archivedRangePresetLabel(preset: AgentSessionArchivedRangePreset): @Nls String {
  return when (preset) {
    AgentSessionArchivedRangePreset.ALL -> AgentSessionsBundle.message("toolwindow.archived.range.all")
    AgentSessionArchivedRangePreset.TODAY -> AgentSessionsBundle.message("toolwindow.archived.range.today")
    AgentSessionArchivedRangePreset.LAST_7_DAYS -> AgentSessionsBundle.message("toolwindow.archived.range.last.7.days")
    AgentSessionArchivedRangePreset.LAST_30_DAYS -> AgentSessionsBundle.message("toolwindow.archived.range.last.30.days")
  }
}

internal class AgentSessionsActivityCounterAction(
  private val bucket: AgentSessionsActivityBucket,
  private val rowsProvider: (Project?, AgentSessionsActivityBucket) -> List<AgentSessionsActivityThreadRow> = ::defaultActivityRowsFor,
  private val visibilityProvider: (Project?) -> Boolean = { isActiveThreadViewMode() },
  private val projectProvider: (AnActionEvent) -> Project? = { event -> event.project },
  private val entryPoint: AgentWorkbenchEntryPoint = AgentWorkbenchEntryPoint.TREE_POPUP,
) : DumbAwareAction(), CustomComponentAction {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val project = projectProvider(e)
    val visible = visibilityProvider(project)
    if (!visible) {
      presentation.setEnabledAndVisible(false)
      ACTIVITY_COUNTER_LOG.debug {
        "Activity counter update bucket=$bucket project=${project?.name} place=${e.place} visible=false"
      }
      return
    }
    val rows = rowsFor(project)
    presentation.text = rows.size.toString()
    presentation.description = AgentSessionsBundle.message(bucket.tooltipKey)
    presentation.setEnabledAndVisible(true)
    presentation.isEnabled = rows.isNotEmpty()
    ACTIVITY_COUNTER_LOG.debug {
      "Activity counter update bucket=$bucket project=${project?.name} place=${e.place} " +
      "rows=${rows.size} text=${presentation.text} enabled=${presentation.isEnabled} visible=${presentation.isVisible}"
    }
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val counter = AgentSessionsActivityCounterComponent(
      action = this,
      accentColor = requireNotNull(bucket.accentActivity().statusColor()) {
        "Activity counter bucket $bucket must define an accent color"
      },
      tone = bucket.counterTone(),
      actionPlace = place,
    )
    counter.update(presentation)
    return counter
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    (component as? AgentSessionsActivityCounterComponent)?.update(presentation)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = projectProvider(e) ?: return
    val component = e.inputEvent?.component ?: return
    val rows = rowsFor(project)
    if (rows.isEmpty()) return
    val now = System.currentTimeMillis()
    val group = DefaultActionGroup().apply {
      rows.forEach { row -> add(AgentSessionsActivityOpenThreadAction(project, row, now, entryPoint)) }
    }
    JBPopupFactory.getInstance()
      .createActionGroupPopup(
        AgentSessionsBundle.message(bucket.popupTitleKey),
        group,
        DataManager.getInstance().getDataContext(component),
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        true,
        null,
        Int.MAX_VALUE,
      )
      .showUnderneathOf(component)
  }

  private fun rowsFor(project: Project?): List<AgentSessionsActivityThreadRow> {
    return rowsProvider(project, bucket)
  }
}

private fun defaultActivityRowsFor(project: Project?, bucket: AgentSessionsActivityBucket): List<AgentSessionsActivityThreadRow> {
  val service = project?.service<AgentSessionsActivityService>() ?: return emptyList()
  return service.latestChromeSummary().rowsFor(bucket)
}

private fun isActiveThreadViewMode(): Boolean {
  return service<AgentSessionThreadViewStateService>().state.value.mode == AgentSessionThreadViewMode.ACTIVE
}

private class AgentSessionsActivityOpenThreadAction(
  private val project: Project,
  private val row: AgentSessionsActivityThreadRow,
  now: Long,
  private val entryPoint: AgentWorkbenchEntryPoint,
) : DumbAwareAction(
  agentSessionsActivityPopupRowText(row, now),
  null,
  agentSessionThreadStatusIcon(row.thread.provider, row.thread.activity),
) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun actionPerformed(e: AnActionEvent) {
    service<AgentSessionLaunchService>().openChatThread(
      path = row.path,
      thread = row.thread,
      entryPoint = entryPoint,
      currentProject = project,
    )
  }
}

internal fun agentSessionsActivityPopupRowText(
  row: AgentSessionsActivityThreadRow,
  now: Long,
): @Nls String {
  val title = StringUtil.shortenTextWithEllipsis(
    threadDisplayTitle(threadId = row.thread.id, title = row.thread.title),
    MAX_POPUP_ROW_TITLE_LENGTH,
    0,
    true,
  )
  val timeLabel = row.thread.updatedAt.takeIf { it > 0 }?.let { timestamp ->
    formatRelativeTimeShort(timestamp, now)
  } ?: AgentSessionsBundle.message("toolwindow.time.unknown")
  return AgentSessionsBundle.message("toolwindow.activity.popup.row", title, row.locationLabel, timeLabel)
}

private val AgentSessionsActivityBucket.tooltipKey: String
  get() = when (this) {
    AgentSessionsActivityBucket.ATTENTION -> "toolwindow.activity.action.attention.tooltip"
    AgentSessionsActivityBucket.RUNNING -> "toolwindow.activity.action.running.tooltip"
    AgentSessionsActivityBucket.DONE -> "toolwindow.activity.action.done.tooltip"
  }

private val AgentSessionsActivityBucket.popupTitleKey: String
  get() = when (this) {
    AgentSessionsActivityBucket.ATTENTION -> "toolwindow.activity.popup.attention.title"
    AgentSessionsActivityBucket.RUNNING -> "toolwindow.activity.popup.running.title"
    AgentSessionsActivityBucket.DONE -> "toolwindow.activity.popup.done.title"
  }

private fun AgentSessionsActivityBucket.accentActivity(): AgentThreadActivity {
  return when (this) {
    AgentSessionsActivityBucket.ATTENTION -> AgentThreadActivity.NEEDS_INPUT
    AgentSessionsActivityBucket.RUNNING -> AgentThreadActivity.PROCESSING
    AgentSessionsActivityBucket.DONE -> AgentThreadActivity.UNREAD
  }
}

private fun AgentSessionsActivityBucket.counterTone(): AgentSessionsActivityCounterTone {
  return when (this) {
    AgentSessionsActivityBucket.ATTENTION -> AgentSessionsActivityCounterTone.ATTENTION
    AgentSessionsActivityBucket.RUNNING,
    AgentSessionsActivityBucket.DONE,
      -> AgentSessionsActivityCounterTone.DEFAULT
  }
}
