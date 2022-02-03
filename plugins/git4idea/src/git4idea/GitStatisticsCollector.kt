// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea

import com.google.common.collect.HashMultiset
import com.intellij.dvcs.branch.DvcsSyncSettings.Value
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.addBoolIfDiffers
import com.intellij.internal.statistic.beans.addIfDiffers
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EnumEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.util.io.URLUtil
import com.intellij.util.io.exists
import com.intellij.util.io.isDirectory
import com.intellij.util.io.isFile
import com.intellij.vcs.log.impl.VcsLogApplicationSettings
import com.intellij.vcs.log.impl.VcsLogProjectTabsProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.VcsLogUiImpl
import git4idea.config.*
import git4idea.repo.GitCommitTemplateTracker
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.ui.branch.dashboard.CHANGE_LOG_FILTER_ON_BRANCH_SELECTION_PROPERTY
import git4idea.ui.branch.dashboard.SHOW_GIT_BRANCHES_LOG_PROPERTY
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class GitStatisticsCollector : ProjectUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(project: Project): MutableSet<MetricEvent> {
    val set = HashSet<MetricEvent>()

    val repositoryManager = GitUtil.getRepositoryManager(project)
    val repositories = repositoryManager.repositories

    val settings = GitVcsSettings.getInstance(project)
    val defaultSettings = GitVcsSettings()

    addIfDiffers(set, settings, defaultSettings, { it.syncSetting }, REPO_SYNC, REPO_SYNC_VALUE)
    addIfDiffers(set, settings, defaultSettings, { it.updateMethod }, UPDATE_TYPE, UPDATE_TYPE_VALUE)
    addIfDiffers(set, settings, defaultSettings, { it.saveChangesPolicy }, SAVE_POLICY, SAVE_POLICY_VALUE)

    addBoolIfDiffers(set, settings, defaultSettings, { it.autoUpdateIfPushRejected() }, PUSH_AUTO_UPDATE)
    addBoolIfDiffers(set, settings, defaultSettings, { it.warnAboutCrlf() }, WARN_CRLF)
    addBoolIfDiffers(set, settings, defaultSettings, { it.warnAboutDetachedHead() }, WARN_DETACHED)

    val appSettings = GitVcsApplicationSettings.getInstance()
    val defaultAppSettings = GitVcsApplicationSettings()

    addBoolIfDiffers(set, appSettings, defaultAppSettings, { it.isStagingAreaEnabled }, STAGING_AREA)

    val version = GitVcs.getInstance(project).version
    set.add(EXECUTABLE.metric(
      EventFields.Version with version.presentation,
      TYPE with version.type
    ))

    for (repository in repositories) {
      val branches = repository.branches

      val repositoryMetric = REPOSITORY.metric(
        LOCAL_BRANCHES with branches.localBranches.size,
        REMOTE_BRANCHES with branches.remoteBranches.size,
        REMOTES with repository.remotes.size,
        WORKING_COPY_SIZE with repository.workingCopySize(),
        IS_WORKTREE_USED with repository.isWorkTreeUsed(),
      )

      val remoteTypes = HashMultiset.create(repository.remotes.mapNotNull { getRemoteServerType(it) })
      for (remoteType in remoteTypes) {
        repositoryMetric.data.addData("remote_$remoteType", remoteTypes.count(remoteType))
      }

      set.add(repositoryMetric)
    }

    addCommitTemplateMetrics(project, repositories, set)

    addGitLogMetrics(project, set)

    return set
  }

  private fun addCommitTemplateMetrics(project: Project, repositories: List<GitRepository>, set: java.util.HashSet<MetricEvent>) {
    if (repositories.isEmpty()) return

    val templatesCount = project.service<GitCommitTemplateTracker>().templatesCount()
    if (templatesCount == 0) return

    set.add(COMMIT_TEMPLATE.metric(
      TEMPLATES_COUNT with templatesCount,
      TEMPLATES_MULTIPLE_ROOTS with (repositories.size > 1)
    ))
  }

  private fun addGitLogMetrics(project: Project, metrics: MutableSet<MetricEvent>) {
    val projectLog = VcsProjectLog.getInstance(project) ?: return
    val ui = projectLog.mainLogUi ?: return

    addPropertyMetricIfDiffers(metrics, ui, SHOW_GIT_BRANCHES_LOG_PROPERTY, SHOW_GIT_BRANCHES_IN_LOG)
    addPropertyMetricIfDiffers(metrics, ui, CHANGE_LOG_FILTER_ON_BRANCH_SELECTION_PROPERTY, UPDATE_BRANCH_FILTERS_ON_SELECTION)
  }

  private fun addPropertyMetricIfDiffers(metrics: MutableSet<MetricEvent>,
                                         ui: VcsLogUiImpl,
                                         property: VcsLogUiProperties.VcsLogUiProperty<Boolean>,
                                         eventId: VarargEventId) {
    val defaultValue = (property as? VcsLogProjectTabsProperties.CustomBooleanTabProperty)?.defaultValue(ui.id)
                       ?: (property as? VcsLogApplicationSettings.CustomBooleanProperty)?.defaultValue() ?: return
    val properties = ui.properties
    val value = if (properties.exists(property)) properties[property] else defaultValue

    if (!Comparing.equal(value, defaultValue)) {
      metrics.add(eventId.metric(EventFields.Enabled with value))
    }
  }

  companion object {
    private val GROUP = EventLogGroup("git.configuration", 5)

    private val REPO_SYNC_VALUE: EnumEventField<Value> = EventFields.Enum("value", Value::class.java) { it.name.lowercase() }
    private val REPO_SYNC: VarargEventId = GROUP.registerVarargEvent("repo.sync", REPO_SYNC_VALUE)

    private val UPDATE_TYPE_VALUE = EventFields.Enum("value", UpdateMethod::class.java) { it.name.lowercase() }
    private val UPDATE_TYPE = GROUP.registerVarargEvent("update.type", UPDATE_TYPE_VALUE)

    private val SAVE_POLICY_VALUE = EventFields.Enum("value", GitSaveChangesPolicy::class.java) { it.name.lowercase() }
    private val SAVE_POLICY = GROUP.registerVarargEvent("save.policy", SAVE_POLICY_VALUE)

    private val PUSH_AUTO_UPDATE = GROUP.registerVarargEvent("push.autoupdate", EventFields.Enabled)
    private val WARN_CRLF = GROUP.registerVarargEvent("warn.about.crlf", EventFields.Enabled)
    private val WARN_DETACHED = GROUP.registerVarargEvent("warn.about.detached", EventFields.Enabled)
    private val STAGING_AREA = GROUP.registerVarargEvent("staging.area.enabled", EventFields.Enabled)

    private val TYPE = EventFields.Enum("type", GitVersion.Type::class.java) { it.name }
    private val EXECUTABLE = GROUP.registerVarargEvent("executable", EventFields.Version, TYPE)

    private val LOCAL_BRANCHES = EventFields.RoundedInt("local_branches")
    private val REMOTE_BRANCHES = EventFields.RoundedInt("remote_branches")
    private val REMOTES = EventFields.RoundedInt("remotes")
    private val WORKING_COPY_SIZE = EventFields.RoundedLong("working_copy_size")
    private val IS_WORKTREE_USED = EventFields.Boolean("is_worktree_used")
    private val remoteTypes = setOf("github", "gitlab", "bitbucket",
                                    "github_custom", "gitlab_custom", "bitbucket_custom")

    private val remoteTypesEventIds = remoteTypes.map {
      EventFields.Int("remote_$it")
    }

    private val REPOSITORY = GROUP.registerVarargEvent("repository",
                                                       LOCAL_BRANCHES,
                                                       REMOTE_BRANCHES,
                                                       REMOTES,
                                                       WORKING_COPY_SIZE,
                                                       IS_WORKTREE_USED,
                                                       *remoteTypesEventIds.toTypedArray()
    )

    private val TEMPLATES_COUNT = EventFields.Int("count")
    private val TEMPLATES_MULTIPLE_ROOTS = EventFields.Boolean("multiple_root")
    private val COMMIT_TEMPLATE = GROUP.registerVarargEvent("commit_template", TEMPLATES_COUNT, TEMPLATES_MULTIPLE_ROOTS)

    private val SHOW_GIT_BRANCHES_IN_LOG = GROUP.registerVarargEvent("showGitBranchesInLog", EventFields.Enabled)
    private val UPDATE_BRANCH_FILTERS_ON_SELECTION = GROUP.registerVarargEvent("updateBranchesFilterInLogOnSelection", EventFields.Enabled)

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
 * Calculates size of work tree in given [GitRepository]
 *
 * @return size in bytes or -1 if some IO error occurs
 */
private fun GitRepository.workingCopySize(): Long = try {
  val root = this.root.toNioPath().toFile()
  root.walk()
    .onEnter { it.name != GitUtil.DOT_GIT && !isInnerRepo(root, it) }
    .filter { it.isFile }
    .sumOf { it.length() }
}
catch (e: Exception) {
  // if something goes wrong with file system operations
  -1
}

private fun isInnerRepo(root: File, dir: File): Boolean {
  if (root == dir) return false

  return Path.of(dir.toString(), GitUtil.DOT_GIT).exists()
}

/**
 * Checks that worktree is used in [GitRepository]
 *
 * worktree usage will be detected when:
 * repo_root/.git is a file
 * or repo_root/.git/worktrees is not empty
 */
private fun GitRepository.isWorkTreeUsed(): Boolean {
  return try {
    val rootPath = this.root.toNioPath()

    val dotGit = Path.of(rootPath.toString(), GitUtil.DOT_GIT)
    if (dotGit.isFile()) return true

    val worktreesPath = repositoryFiles.worktreesDirFile.toPath()
    if (!worktreesPath.exists()) return false
    if (!worktreesPath.isDirectory()) return false

    Files.list(worktreesPath).count() > 0
  }
  catch (e: Exception) {
    false
  }
}