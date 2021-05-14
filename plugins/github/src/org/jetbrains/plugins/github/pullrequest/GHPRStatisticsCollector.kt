// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GHEnterpriseServerMeta
import org.jetbrains.plugins.github.api.data.GithubPullRequestMergeMethod

object GHPRStatisticsCollector {

  private val COUNTERS_GROUP = EventLogGroup("vcs.github.pullrequest.counters", 2)

  class Counters : CounterUsagesCollector() {
    override fun getGroup() = COUNTERS_GROUP
  }

  private val TIMELINE_OPENED_EVENT = COUNTERS_GROUP.registerEvent("timeline.opened", EventFields.Int("count"))
  private val DIFF_OPENED_EVENT = COUNTERS_GROUP.registerEvent("diff.opened", EventFields.Int("count"))
  private val MERGED_EVENT = COUNTERS_GROUP.registerEvent("merged", EventFields.Enum<GithubPullRequestMergeMethod>("method") {
    it.name.toUpperCase()
  })
  private val anonymizedId = object : PrimitiveEventField<String>() {

    override val name = "anonymized_id"

    override fun addData(fuData: FeatureUsageData, value: String) {
      fuData.addAnonymizedId(value)
    }

    override val validationRule: List<String>
      get() = listOf("{regexp#hash}")
  }
  private val SERVER_META_EVENT = COUNTERS_GROUP.registerEvent("server.meta.collected", anonymizedId, EventFields.Version)

  fun logTimelineOpened(project: Project) {
    val count = FileEditorManager.getInstance(project).openFiles.count { it is GHPRTimelineVirtualFile }
    TIMELINE_OPENED_EVENT.log(project, count)
  }

  fun logDiffOpened(project: Project) {
    val count = FileEditorManager.getInstance(project).openFiles.count { it is GHPRDiffVirtualFile }
    DIFF_OPENED_EVENT.log(project, count)
  }

  fun logMergedEvent(method: GithubPullRequestMergeMethod) {
    MERGED_EVENT.log(method)
  }

  fun logEnterpriseServerMeta(project: Project, server: GithubServerPath, meta: GHEnterpriseServerMeta) {
    SERVER_META_EVENT.log(project, server.toUrl(), meta.installedVersion)
  }
}