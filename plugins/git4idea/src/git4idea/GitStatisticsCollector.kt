// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea

import com.google.common.collect.HashMultiset
import com.intellij.internal.statistic.beans.*
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.util.io.URLUtil
import com.intellij.util.io.exists
import com.intellij.vcs.log.impl.VcsLogApplicationSettings
import com.intellij.vcs.log.impl.VcsLogProjectTabsProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.VcsLogUiImpl
import git4idea.config.GitVcsApplicationSettings
import git4idea.config.GitVcsSettings
import git4idea.repo.GitCommitTemplateTracker
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.ui.branch.dashboard.CHANGE_LOG_FILTER_ON_BRANCH_SELECTION_PROPERTY
import git4idea.ui.branch.dashboard.SHOW_GIT_BRANCHES_LOG_PROPERTY
import java.io.File
import java.nio.file.Path

class GitStatisticsCollector : ProjectUsagesCollector() {
  override fun getGroupId(): String = "git.configuration"
  override fun getVersion(): Int = 5

  override fun getMetrics(project: Project): MutableSet<MetricEvent> {
    val set = HashSet<MetricEvent>()

    val repositoryManager = GitUtil.getRepositoryManager(project)
    val repositories = repositoryManager.repositories

    val settings = GitVcsSettings.getInstance(project)
    val defaultSettings = GitVcsSettings()

    addEnumIfDiffers(set, settings, defaultSettings, { it.syncSetting }, "repo.sync")
    addEnumIfDiffers(set, settings, defaultSettings, { it.updateMethod }, "update.type")
    addEnumIfDiffers(set, settings, defaultSettings, { it.saveChangesPolicy }, "save.policy")

    addBoolIfDiffers(set, settings, defaultSettings, { it.autoUpdateIfPushRejected() }, "push.autoupdate")
    addBoolIfDiffers(set, settings, defaultSettings, { it.warnAboutCrlf() }, "warn.about.crlf")
    addBoolIfDiffers(set, settings, defaultSettings, { it.warnAboutDetachedHead() }, "warn.about.detached")

    val appSettings = GitVcsApplicationSettings.getInstance()
    val defaultAppSettings = GitVcsApplicationSettings()

    addBoolIfDiffers(set, appSettings, defaultAppSettings, { it.isStagingAreaEnabled }, "staging.area.enabled")

    val version = GitVcs.getInstance(project).version
    set.add(newMetric("executable", FeatureUsageData().addData("version", version.presentation).addData("type", version.type.name)))

    for (repository in repositories) {
      val branches = repository.branches

      val metric = newMetric("repository")
      metric.data.addData("local_branches", branches.localBranches.size)
      metric.data.addData("remote_branches", branches.remoteBranches.size)
      metric.data.addData("remotes", repository.remotes.size)
      metric.data.addData("working_copy_size", repository.workingCopySize())

      val remoteTypes = HashMultiset.create(repository.remotes.mapNotNull { getRemoteServerType(it) })
      for (remoteType in remoteTypes) {
        metric.data.addData("remote_$remoteType", remoteTypes.count(remoteType))
      }

      set.add(metric)
    }

    addCommitTemplateMetrics(project, repositories, set)

    addGitLogMetrics(project, set)

    return set
  }

  private fun addCommitTemplateMetrics(project: Project, repositories: List<GitRepository>, set: java.util.HashSet<MetricEvent>) {
    if (repositories.isEmpty()) return

    val templatesCount = project.service<GitCommitTemplateTracker>().templatesCount()
    if (templatesCount == 0) return

    val metric = newMetric("commit_template")
    metric.data.addData("count", templatesCount)
    metric.data.addData("multiple_root", repositories.size > 1)
    set.add(metric)
  }

  private fun addGitLogMetrics(project: Project, metrics: MutableSet<MetricEvent>) {
    val projectLog = VcsProjectLog.getInstance(project) ?: return
    val ui = projectLog.mainLogUi ?: return

    addPropertyMetricIfDiffers(metrics, ui, SHOW_GIT_BRANCHES_LOG_PROPERTY, "showGitBranchesInLog")
    addPropertyMetricIfDiffers(metrics, ui, CHANGE_LOG_FILTER_ON_BRANCH_SELECTION_PROPERTY, "updateBranchesFilterInLogOnSelection")
  }

  private fun addPropertyMetricIfDiffers(metrics: MutableSet<MetricEvent>,
                                         ui: VcsLogUiImpl,
                                         property: VcsLogUiProperties.VcsLogUiProperty<Boolean>,
                                         eventId: String) {
    val defaultValue = (property as? VcsLogProjectTabsProperties.CustomBooleanTabProperty)?.defaultValue(ui.id)
                       ?: (property as? VcsLogApplicationSettings.CustomBooleanProperty)?.defaultValue() ?: return
    val properties = ui.properties
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

/**
 * Calculates size of work tree in given [GitRepository] and rounds it to the power of two
 *
 * @return size in bytes or -1 if some IO error occurs
 */
private fun GitRepository.workingCopySize(): Long = try {
  val root = this.root.toNioPath().toFile()
  val sizeInBytes = root
    .walk()
    .onEnter { it.name != GitUtil.DOT_GIT && !isInnerRepo(root, it) }
    .filter { it.isFile }
    .sumOf { it.length() }
  StatisticsUtil.roundToPowerOfTwo(sizeInBytes)
}
catch (e: Exception) {
  // if something goes wrong with file system operations
  -1
}

private fun isInnerRepo(root: File, dir: File): Boolean {
  if (root == dir) return false

  return Path.of(dir.toString(), GitUtil.DOT_GIT).exists()
}