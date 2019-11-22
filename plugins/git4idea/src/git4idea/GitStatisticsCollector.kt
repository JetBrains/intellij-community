// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea

import com.google.common.collect.HashMultiset
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.addBoolIfDiffers
import com.intellij.internal.statistic.beans.addEnumIfDiffers
import com.intellij.internal.statistic.beans.newMetric
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.util.io.URLUtil
import git4idea.config.GitVcsApplicationSettings
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRemote
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
    addEnumIfDiffers(set, settings, defaultSettings, { it.updateChangesPolicy() }, "save.policy")
    addBoolIfDiffers(set, appSettings, defaultAppSettings, { it.isUseIdeaSsh }, "use.builtin.ssh")

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

    return set
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
