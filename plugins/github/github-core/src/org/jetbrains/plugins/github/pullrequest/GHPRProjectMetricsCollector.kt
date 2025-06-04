// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUtil.roundToPowerOfTwo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GHPullRequestMetrics
import org.jetbrains.plugins.github.api.executeSuspend
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager

/**
 * Collects statistics about the GitHub project in the background.
 */
private class GHPRProjectMetricsCollector : ProjectUsagesCollector() {
  @Suppress("CompanionObjectInExtension")
  private companion object {
    private val GROUP = EventLogGroup(
      "vcs.github.project", 1,
      recorder = "FUS",
      description = "Collects metrics about GitHub Plugin usage."
    )

    private val PR_STATISTICS_ALL = GROUP.registerEvent(
      "pr.statistics.all",
      EventFields.Int("value", description = "Total number of PRs in project (rounded up to the first power of 2)."),
      description = "#PR statistics: open."
    )
    private val PR_STATISTICS_OPEN = GROUP.registerEvent(
      "pr.statistics.open",
      EventFields.Int("value", description = "Total number of open PRs in project (rounded up to the first power of 2)."),
      description = "#PR statistics: open."
    )
    private val PR_STATISTICS_OPEN_AUTHOR = GROUP.registerEvent(
      "pr.statistics.open.author",
      EventFields.Int("value",
                      description = "Total number of open PRs in project authored by the current user (rounded up to the first power of 2)."),
      description = "#PR statistics: open > author."
    )
    private val PR_STATISTICS_OPEN_ASSIGNEE = GROUP.registerEvent(
      "pr.statistics.open.assignee",
      EventFields.Int("value",
                      description = "Total number of open PRs in project assigned to the current user (rounded up to the first power of 2)."),
      description = "#PR statistics: open > assignee."
    )
    private val PR_STATISTICS_OPEN_REVIEW_ASSIGNED = GROUP.registerEvent(
      "pr.statistics.open.reviewer",
      EventFields.Int("value",
                      description = "Total number of open PRs in project assigned to the current user as reviewer (rounded up to the first power of 2)."),
      description = "#PR statistics: open > reviewer."
    )
    private val PR_STATISTICS_OPEN_REVIEWED = GROUP.registerEvent(
      "pr.statistics.open.reviewed",
      EventFields.Int("value",
                      description = "Total number of open PRs in project reviewed by the current user (rounded up to the first power of 2)."),
      description = "#PR statistics: open > reviewed."
    )
  }

  override fun getGroup(): EventLogGroup? = GROUP

  override suspend fun collect(project: Project): Set<MetricEvent> {
    val metricsLoader = project.serviceAsync<GHPRMetricsLoader>()
    val metrics = metricsLoader.getMetrics() ?: return emptySet()

    return setOfNotNull(
      PR_STATISTICS_ALL.metric(roundToPowerOfTwo(metrics.allPRCount.issueCount)),
      PR_STATISTICS_OPEN.metric(roundToPowerOfTwo(metrics.openPRCount.issueCount)),
      PR_STATISTICS_OPEN_AUTHOR.metric(roundToPowerOfTwo(metrics.openAuthoredPRCount.issueCount)),
      PR_STATISTICS_OPEN_ASSIGNEE.metric(roundToPowerOfTwo(metrics.openAssigneePRCount.issueCount)),
      PR_STATISTICS_OPEN_REVIEW_ASSIGNED.metric(roundToPowerOfTwo(metrics.openReviewAssignedPRCount.issueCount)),
      PR_STATISTICS_OPEN_REVIEWED.metric(roundToPowerOfTwo(metrics.openReviewedPRCount.issueCount)),
    )
  }
}

@Service(Service.Level.PROJECT)
internal class GHPRMetricsLoader(private val project: Project) {
  companion object {
    private val LOG = logger<GHPRMetricsLoader>()
  }

  private suspend fun getExecutor(serverPath: GithubServerPath): GithubApiRequestExecutor? {
    val accountManager = serviceAsync<GHAccountManager>()
    val defaultAccountManager = project.serviceAsync<GithubProjectDefaultAccountHolder>()

    val account = defaultAccountManager.account.takeIf { it?.server == serverPath }
                  ?: accountManager.accountsState.value.firstOrNull { it.server == serverPath }
                  ?: return null
    val token = accountManager.findCredentials(account) ?: return null
    return GithubApiRequestExecutor.Factory.getInstance().create(serverPath, token)
  }

  private suspend fun chooseRepo(): GHGitRepositoryMapping? {
    val repositoriesManager = project.serviceAsync<GHHostedRepositoriesManager>()
    val repositoriesState = repositoriesManager.knownRepositoriesState

    val knownRepos = repositoriesState.value

    if (knownRepos.isEmpty()) return null
    if (knownRepos.size == 1) return knownRepos.single()

    val nonFork = withContext(Dispatchers.IO) {
      knownRepos.firstOrNull { repo ->
        val executor = getExecutor(repo.repository.serverPath) ?: return@firstOrNull false
        executor.executeSuspend(GHGQLRequests.Repo.find(repo.repository))?.isFork == false
      }
    }
    if (nonFork != null) return nonFork

    return knownRepos.find { it.gitRemote.name == "origin" }
           ?: knownRepos.firstOrNull()
  }

  suspend fun getMetrics(): GHPullRequestMetrics? {
    try {
      val chosenRepoMapping = chooseRepo() ?: return null

      val executor = getExecutor(chosenRepoMapping.repository.serverPath) ?: return null
      return executor.executeSuspend(GHGQLRequests.PullRequest.metrics(chosenRepoMapping.repository))
    }
    catch (e: Exception) {
      LOG.warn("Failed to load metrics", e)
      return null
    }
  }
}
