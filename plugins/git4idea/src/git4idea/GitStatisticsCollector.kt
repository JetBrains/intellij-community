// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea

import com.google.common.collect.HashMultiset
import com.intellij.internal.statistic.beans.*
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.util.io.URLUtil
import com.intellij.vcs.log.impl.VcsLogProjectTabsProperties
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.VcsLogUiImpl
import git4idea.config.GitVcsApplicationSettings
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRemote
import git4idea.ui.branch.dashboard.SHOW_GIT_BRANCHES_LOG_PROPERTY
import java.util.*

class GitStatisticsCollector : ProjectUsagesCollector() {
  override fun getGroupId(): String = "git.configuration"
  override fun getVersion(): Int = 2

  override fun getMetrics(project: Project): MutableSet<MetricEvent> {
    val set = HashSet<MetricEvent>()

    val repositoryManager = GitUtil.getRepositoryManager(project)
    val repositories = repositoryManager.repositories

    val appSettings = GitVcsApplicationSettings.getInstance()
    val defaultAppSettings = GitVcsApplicationSettings()

    val settings = GitVcsSettings.getInstance(project)
    val defaultSettings = GitVcsSettings()

    addEnumIfDiffers(set, settings, defaultSettings, { it.syncSetting }, "repo.sync")
    addEnumIfDiffers(set, settings, defaultSettings, { it.updateMethod }, "update.type")
    addEnumIfDiffers(set, settings, defaultSettings, { it.saveChangesPolicy }, "save.policy")

    addBoolIfDiffers(set, settings, defaultSettings, { it.autoUpdateIfPushRejected() }, "push.autoupdate")
    addBoolIfDiffers(set, settings, defaultSettings, { it.shouldUpdateAllRootsIfPushRejected() }, "push.update.all.roots")
    addBoolIfDiffers(set, appSettings, defaultAppSettings, { it.isAutoCommitOnCherryPick }, "cherrypick.autocommit")
    addBoolIfDiffers(set, settings, defaultSettings, { it.warnAboutCrlf() }, "warn.about.crlf")
    addBoolIfDiffers(set, settings, defaultSettings, { it.warnAboutDetachedHead() }, "warn.about.detached")

    val version = GitVcs.getInstance(project).version
    set.add(newMetric("executable", FeatureUsageData().addData("version", version.presentation).addData("type", version.type.name)))

    for (repository in repositories) {
      val branches = repository.branches

      val metric = newMetric("repository")
      metric.data.addData("local_branches", branches.localBranches.size)
      metric.data.addData("remote_branches", branches.remoteBranches.size)
      metric.data.addData("remotes", repository.remotes.size)

      val remoteTypes = HashMultiset.create<String>(repository.remotes.mapNotNull { getRemoteServerType(it) })
      for (remoteType in remoteTypes) {
        metric.data.addData("remote_$remoteType", remoteTypes.count(remoteType))
      }

      set.add(metric)
    }

    addGitLogMetrics(project, set)

    return set
  }

  private fun addGitLogMetrics(project: Project, metrics: MutableSet<MetricEvent>) {
    val projectLog = VcsProjectLog.getInstance(project) ?: return
    val ui = projectLog.mainLogUi ?: return

    addPropertyMetricIfDiffers(metrics, ui, SHOW_GIT_BRANCHES_LOG_PROPERTY, "showGitBranchesInLog")
  }

  private fun addPropertyMetricIfDiffers(metrics: MutableSet<MetricEvent>,
                                         ui: VcsLogUiImpl,
                                         property: VcsLogProjectTabsProperties.CustomBooleanTabProperty,
                                         eventId: String) {
    val properties = ui.properties
    val defaultValue = property.defaultValue(ui.id)
    val value = if (properties.exists(property)) properties[property] else defaultValue

    if (!Comparing.equal(value, defaultValue)) {
      metrics.add(newBooleanMetric(eventId, value))
    }
  }

  companion object {
    private fun getRemoteServerType(remote: GitRemote): String? {
      val hosts = remote.urls.map(URLUtil::parseHostFromSshUrl).distinct()

      if (hosts.contains("github.com")) return "github"
      if (hosts.contains("gitlab.com")) return "gitlab"
      if (hosts.contains("bitbucket.org")) return "bitbucket"

      if (remote.urls.any { it.contains("github") }) return "github_custom"
      if (remote.urls.any { it.contains("gitlab") }) return "gitlab_custom"
      if (remote.urls.any { it.contains("bitbucket") }) return "bitbucket_custom"

      return null
    }
  }
}
