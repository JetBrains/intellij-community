// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.newMetric
import com.intellij.internal.statistic.eventLog.EventField
import com.intellij.internal.statistic.eventLog.EventFields
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GHEnterpriseServerMeta
import org.jetbrains.plugins.github.api.data.GithubPullRequestMergeMethod

object GHPRStatisticsCollector : ProjectUsagesCollector() {

  private val STATE_GROUP = EventLogGroup("vcs.github.pullrequests", 1)

  class ProjectState : ProjectUsagesCollector() {
    override fun getMetrics(project: Project): Set<MetricEvent> {
      val contents = project.service<ToolWindowManager>().getToolWindow(GHPRToolWindowFactory.ID)
        ?.contentManagerIfCreated?.contents

      val tabsCount = contents?.size ?: 0
      val initializedTabsCount = contents?.count {
        it.getUserData(GHPRToolWindowTabsContentManager.INIT_DONE_KEY) != null
      } ?: 0

      return setOf(
        newMetric("toolwindow", FeatureUsageData()
          .addData("tabs", tabsCount)
          .addData("initialized_tabs", initializedTabsCount)))
    }

    override fun getGroup() = STATE_GROUP
  }

  private val COUNTERS_GROUP = EventLogGroup("vcs.github.pullrequest.counters", 1)

  class Counters : CounterUsagesCollector() {
    override fun getGroup() = COUNTERS_GROUP
  }

  private val TIMELINE_OPENED_EVENT = COUNTERS_GROUP.registerEvent("timeline.opened", EventFields.Int("count"))
  private val DIFF_OPENED_EVENT = COUNTERS_GROUP.registerEvent("diff.opened", EventFields.Int("count"))
  private val MERGED_EVENT = COUNTERS_GROUP.registerEvent("merged", EventFields.Enum<GithubPullRequestMergeMethod>("method") {
    it.name.toUpperCase()
  })
  private val anonymizedId = object : EventField<String>() {

    override val name = "anonymized_id"

    override fun addData(fuData: FeatureUsageData, value: String) {
      fuData.addAnonymizedId(value)
    }
  }
  private val SERVER_META_EVENT = COUNTERS_GROUP.registerEvent("server.meta.collected", anonymizedId, EventFields.Version)

  fun logTimelineOpened(project: Project) {
    val count = FileEditorManager.getInstance(project).openFiles.count { it is GHPRTimelineVirtualFile }
    TIMELINE_OPENED_EVENT.log(count)
  }

  fun logDiffOpened(project: Project) {
    val count = FileEditorManager.getInstance(project).openFiles.count { it is GHPRDiffVirtualFile }
    DIFF_OPENED_EVENT.log(count)
  }

  fun logMergedEvent(method: GithubPullRequestMergeMethod) {
    MERGED_EVENT.log(method)
  }

  fun logEnterpriseServerMeta(server: GithubServerPath, meta: GHEnterpriseServerMeta) {
    SERVER_META_EVENT.log(server.toUrl(), meta.installedVersion)
  }
}