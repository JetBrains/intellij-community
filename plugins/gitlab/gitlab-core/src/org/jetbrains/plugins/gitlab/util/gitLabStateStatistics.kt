// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.util

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.GitLabProjectsManager
import org.jetbrains.plugins.gitlab.api.GitLabApi
import org.jetbrains.plugins.gitlab.api.GitLabApiManager
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.api.getClient
import org.jetbrains.plugins.gitlab.api.request.getCurrentUser
import org.jetbrains.plugins.gitlab.api.request.isProjectForked
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabProjectDefaultAccountHolder
import org.jetbrains.plugins.gitlab.mergerequest.api.dto.GitLabMergeRequestMetricsDTO
import org.jetbrains.plugins.gitlab.mergerequest.api.request.getMergeRequestMetrics

@ApiStatus.Internal
object GitLabStateStatistics {
  //region Application
  private val STATE_GROUP = EventLogGroup("vcs.gitlab", 1)

  private val ACCOUNTS_EVENT: EventId2<Int, Boolean> =
    STATE_GROUP.registerEvent("accounts", EventFields.Count, EventFields.Boolean("has_enterprise"))

  internal class GitLabAccountsStatisticsCollector : ApplicationUsagesCollector() {
    override fun getGroup(): EventLogGroup = STATE_GROUP

    override fun getMetrics(): Set<MetricEvent> {
      val accountManager = service<GitLabAccountManager>()
      val hasAccountsWithNonDefaultHost = accountManager.accountsState.value.any { !it.server.isDefault }

      return setOf(ACCOUNTS_EVENT.metric(accountManager.accountsState.value.size, hasAccountsWithNonDefaultHost))
    }
  }
  //endregion

  //region Project
  private val PROJECT_METRICS_GROUP = EventLogGroup("vcs.gitlab.project", 1)

  private val PROJECT_METRICS_MR_STATISTICS_ALL = PROJECT_METRICS_GROUP.registerEvent(
    "mr.statistics.all",
    EventFields.RoundedInt("value", description = "Total number of MRs in project (rounded up to the first power of 2).")
  )
  private val PROJECT_METRICS_MR_STATISTICS_OPEN = PROJECT_METRICS_GROUP.registerEvent(
    "mr.statistics.open",
    EventFields.RoundedInt("value", description = "Total number of open MRs in project (rounded up to the first power of 2)."),
  )
  private val PROJECT_METRICS_MR_STATISTICS_OPEN_AUTHOR = PROJECT_METRICS_GROUP.registerEvent(
    "mr.statistics.open.author",
    EventFields.RoundedInt("value", description = "Total number of open MRs in project authored by the current user (rounded up to the first power of 2)."),
  )
  private val PROJECT_METRICS_MR_STATISTICS_OPEN_ASSIGNEE = PROJECT_METRICS_GROUP.registerEvent(
    "mr.statistics.open.assignee",
    EventFields.RoundedInt("value", description = "Total number of open MRs in project assigned to the current user (rounded up to the first power of 2)."),
  )
  private val PROJECT_METRICS_MR_STATISTICS_OPEN_REVIEW_ASSIGNED = PROJECT_METRICS_GROUP.registerEvent(
    "mr.statistics.open.reviewer",
    EventFields.RoundedInt("value", description = "Total number of open MRs in project assigned to the current user as reviewer (rounded up to the first power of 2)."),
  )

  internal class GitLabProjectMetricsCollector : ProjectUsagesCollector() {
    override fun getGroup(): EventLogGroup = PROJECT_METRICS_GROUP

    override suspend fun collect(project: Project): Set<MetricEvent> {
      val metricsLoader = project.serviceAsync<GitLabMetricsLoader>()
      val metrics = metricsLoader.getMetrics() ?: return emptySet()

      return setOfNotNull(
        PROJECT_METRICS_MR_STATISTICS_ALL.metric(metrics.allMRCount.count),
        PROJECT_METRICS_MR_STATISTICS_OPEN.metric(metrics.openMRCount.count),
        PROJECT_METRICS_MR_STATISTICS_OPEN_AUTHOR.metric(metrics.openAuthoredMRCount.count),
        PROJECT_METRICS_MR_STATISTICS_OPEN_ASSIGNEE.metric(metrics.openAssignedMRCount.count),
        PROJECT_METRICS_MR_STATISTICS_OPEN_REVIEW_ASSIGNED.metric(metrics.openReviewAssignedMRCount.count),
      )
    }
  }
  //endregion
}

@Service(Service.Level.PROJECT)
internal class GitLabMetricsLoader(private val project: Project) {
  companion object {
    private val LOG = logger<GitLabMetricsLoader>()
  }

  private suspend fun getApi(serverPath: GitLabServerPath): GitLabApi? {
    val accountManager = serviceAsync<GitLabAccountManager>()
    val defaultAccountManager = project.serviceAsync<GitLabProjectDefaultAccountHolder>()

    val account = defaultAccountManager.account.takeIf { it?.server == serverPath }
                  ?: accountManager.accountsState.value.firstOrNull { it.server == serverPath }
                  ?: return null
    return service<GitLabApiManager>().getClient(account)
  }

  private suspend fun chooseRepo(): GitLabProjectMapping? {
    val repositoriesManager = project.serviceAsync<GitLabProjectsManager>()
    val repositoriesState = repositoriesManager.knownRepositoriesState

    val knownRepos = repositoriesState.value

    if (knownRepos.isEmpty()) return null
    if (knownRepos.size == 1) return knownRepos.single()

    val nonFork = withContext(Dispatchers.IO) {
      knownRepos.firstOrNull { repo ->
        val api = getApi(repo.repository.serverPath) ?: return@firstOrNull false
        !api.isProjectForked(repo.repository.projectPath)
      }
    }
    if (nonFork != null) return nonFork

    return knownRepos.find { it.gitRemote.name == "origin" }
           ?: knownRepos.firstOrNull()
  }

  suspend fun getMetrics(): GitLabMergeRequestMetricsDTO? {
    try {
      val chosenRepoMapping = chooseRepo() ?: return null

      val api = getApi(chosenRepoMapping.repository.serverPath) ?: return null
      val currentUser = api.graphQL.getCurrentUser()
      return api.graphQL.getMergeRequestMetrics(chosenRepoMapping.repository.projectPath, currentUser.username)
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      LOG.warn("Failed to load metrics", e)
      return null
    }
  }
}
