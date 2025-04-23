// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.google.common.collect.HashMultiset
import com.intellij.dvcs.branch.DvcsSyncSettings.Value
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.addBoolIfDiffers
import com.intellij.internal.statistic.beans.addIfDiffers
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.EnumEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vcs.VcsException
import com.intellij.util.io.URLUtil
import com.intellij.vcs.log.impl.VcsLogApplicationSettings
import com.intellij.vcs.log.impl.VcsLogProjectTabsProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.VcsLogUiImpl
import com.intellij.vcsUtil.VcsUtil
import git4idea.branch.GitBranchUtil
import git4idea.config.*
import git4idea.index.getStatus
import git4idea.repo.GitCommitTemplateTracker
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.statistics.GitAvailabilityChecker
import git4idea.statistics.GitCommitterCounter
import git4idea.statistics.RepositoryAvailability
import git4idea.ui.branch.dashboard.CHANGE_LOG_FILTER_ON_BRANCH_SELECTION_PROPERTY
import git4idea.ui.branch.dashboard.SHOW_GIT_BRANCHES_LOG_PROPERTY
import org.jetbrains.annotations.NonNls
import java.nio.file.Files
import java.nio.file.Path
import java.time.Period
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

internal class GitStatisticsCollector : ProjectUsagesCollector() {
  private val GROUP = EventLogGroup("git.configuration", 19)

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(project: Project): Set<MetricEvent> {
    if (!TrustedProjects.isProjectTrusted(project)) return emptySet()

    val set = HashSet<MetricEvent>()

    val repositoryManager = GitUtil.getRepositoryManager(project)
    val repositoryChecker = GitAvailabilityChecker.getInstance(project)
    val repositories = repositoryManager.repositories

    val settings = GitVcsSettings.getInstance(project)
    val defaultSettings = GitVcsSettings(project)

    addIfDiffers(set, settings, defaultSettings, { it.syncSetting }, REPO_SYNC, REPO_SYNC_VALUE)
    addIfDiffers(set, settings, defaultSettings, { it.updateMethod }, UPDATE_TYPE, UPDATE_TYPE_VALUE)
    addIfDiffers(set, settings, defaultSettings, { it.saveChangesPolicy }, SAVE_POLICY, SAVE_POLICY_VALUE)

    addBoolIfDiffers(set, settings, defaultSettings, { it.autoUpdateIfPushRejected() }, PUSH_AUTO_UPDATE)
    addBoolIfDiffers(set, settings, defaultSettings, { it.warnAboutCrlf() }, WARN_CRLF)
    addBoolIfDiffers(set, settings, defaultSettings, { it.warnAboutDetachedHead() }, WARN_DETACHED)
    addBoolIfDiffers(set, settings, defaultSettings, { it.warnAboutLargeFiles() }, WARN_LARGE_FILES)
    addBoolIfDiffers(set, settings, defaultSettings, { it.warnAboutBadFileNames() }, WARN_BAD_FILE_NAMES)

    addBoolIfDiffers(set, settings, defaultSettings, { it.filterByActionInPopup() }, FILTER_BY_ACTION_IN_POPUP)
    addBoolIfDiffers(set, settings, defaultSettings, { it.filterByRepositoryInPopup() }, FILTER_BY_REPOSITORY_IN_POPUP)

    val appSettings = GitVcsApplicationSettings.getInstance()
    val defaultAppSettings = GitVcsApplicationSettings()

    addBoolIfDiffers(set, appSettings, defaultAppSettings, { it.isStagingAreaEnabled }, STAGING_AREA)

    reportVersion(project, set)
    val counter = GitCommitterCounter(listOf(Period.ofMonths(1), Period.ofMonths(3), Period.ofYears(1)),
                                      additionalGitParameters = listOf("--all"))
    val executable = GitExecutableManager.getInstance().getExecutable(null)

    for (repository in repositories) {
      val repoStatus = repositoryChecker.checkRepoStatus(repository)
      val branches = repository.branches
      val repositoryMetric = REPOSITORY.metric(
        REPO_ID with project.getProjectCacheFileName() + repository.root.name,
        LOCAL_BRANCHES with branches.localBranches.size,
        REMOTE_BRANCHES with branches.remoteBranches.size,
        RECENT_CHECKOUT_BRANCHES with branches.recentCheckoutBranches.size,
        REMOTES with repository.remotes.size,
        IS_WORKTREE_USED with repository.isWorkTreeUsed(),
        FS_MONITOR with repository.detectFsMonitor(),

        REMOTES_AVAILABILITY with repoStatus,
      )

      try {
        val commitersCount = counter.calculateWithGit(project, repository)
        if (commitersCount.size == 3) {
          COMMITERS_LAST_MONTH.addData(repositoryMetric.data, commitersCount[0].authors)
          COMMITERS_HALF_YEAR.addData(repositoryMetric.data, commitersCount[1].authors)
          COMMITERS_LAST_YEAR.addData(repositoryMetric.data, commitersCount[2].authors)
        }
      }
      catch (e: Exception) {
        if (e is ControlFlowException) throw e
        Logger.getInstance(GitStatisticsCollector::class.java).warn(e)
      }
      val remoteTypes = HashMultiset.create(repository.remotes.map { getRemoteServerType(it) })
      for (remoteType in remoteTypes) {
        repositoryMetric.data.addData("remote_$remoteType", remoteTypes.count(remoteType))
      }
      set.add(repositoryMetric)

      for (configDirName in ALL_IDE_CONFIG_NAMES) {
        getConfigFileStatus(repository, configDirName)?.let { status ->
          set.add(SHARED_IDE_CONFIG.metric(
            IDE_CONFIG_NAME.with(configDirName),
            IDE_CONFIG_STATUS.with(status)
          ))
        }
      }
    }

    addRecentBranchesOptionMetric(set, settings, defaultSettings, repositories)

    addCommonBranchesMetrics(repositories, set)

    addCommitTemplateMetrics(project, repositories, set)

    addGitLogMetrics(project, set)

    return set
  }

  private fun reportVersion(project: Project, set: MutableSet<MetricEvent>) {
    val executableManager = GitExecutableManager.getInstance()
    val version = executableManager.getVersionOrIdentifyIfNeeded(project)

    set.add(EXECUTABLE.metric(
      EventFields.Version with version.presentation,
      TYPE with version.type
    ))
  }

  private fun addRecentBranchesOptionMetric(
    set: MutableSet<MetricEvent>,
    settings: GitVcsSettings,
    defaultSettings: GitVcsSettings,
    repositories: List<GitRepository>,
  ) {
    if (defaultSettings.showRecentBranches() == settings.showRecentBranches()) return

    val maxLocalBranches = repositories.maxOf { repo -> repo.branches.localBranches.size }
    set.add(SHOW_RECENT_BRANCHES.metric(EventFields.Enabled with settings.showRecentBranches(), MAX_LOCAL_BRANCHES with maxLocalBranches))
  }

  private fun addCommonBranchesMetrics(repositories: List<GitRepository>, set: MutableSet<MetricEvent>) {
    val commonLocalBranches = GitBranchUtil.getCommonLocalBranches(repositories)
    val commonRemoteBranches = GitBranchUtil.getCommonRemoteBranches(repositories)
    if (commonLocalBranches.isEmpty() && commonRemoteBranches.isEmpty()) return

    set.add(COMMON_BRANCHES_COUNT_EVENT.metric(
      COMMON_LOCAL_BRANCHES with commonLocalBranches.size,
      COMMON_REMOTE_BRANCHES with commonRemoteBranches.size
    ))
  }

  private fun addCommitTemplateMetrics(project: Project, repositories: List<GitRepository>, set: MutableSet<MetricEvent>) {
    if (repositories.isEmpty()) return

    val templatesCount = GitCommitTemplateTracker.getInstance(project).templatesCount()
    if (templatesCount == 0) return

    set.add(COMMIT_TEMPLATE.metric(
      TEMPLATES_COUNT with templatesCount,
      TEMPLATES_MULTIPLE_ROOTS with (repositories.size > 1)
    ))
  }

  private fun addGitLogMetrics(project: Project, metrics: MutableSet<MetricEvent>) {
    val projectLog = project.serviceIfCreated<VcsProjectLog>() ?: return
    val ui = projectLog.mainLogUi ?: return

    addPropertyMetricIfDiffers(metrics, ui, SHOW_GIT_BRANCHES_LOG_PROPERTY, SHOW_GIT_BRANCHES_IN_LOG)
    addPropertyMetricIfDiffers(metrics, ui, CHANGE_LOG_FILTER_ON_BRANCH_SELECTION_PROPERTY, UPDATE_BRANCH_FILTERS_ON_SELECTION)
  }

  private fun addPropertyMetricIfDiffers(
    metrics: MutableSet<MetricEvent>,
    ui: VcsLogUiImpl,
    property: VcsLogUiProperties.VcsLogUiProperty<Boolean>,
    eventId: VarargEventId,
  ) {
    val defaultValue = (property as? VcsLogProjectTabsProperties.CustomBooleanTabProperty)?.defaultValue(ui.id)
                       ?: (property as? VcsLogApplicationSettings.CustomBooleanProperty)?.defaultValue() ?: return
    val properties = ui.properties
    val value = if (properties.exists(property)) properties[property] else defaultValue

    if (!Comparing.equal(value, defaultValue)) {
      metrics.add(eventId.metric(EventFields.Enabled with value))
    }
  }

  private val REPO_SYNC_VALUE: EnumEventField<Value> = EventFields.Enum("value", Value::class.java) { it.name.lowercase() }
  private val REPO_SYNC: VarargEventId = GROUP.registerVarargEvent("repo.sync", REPO_SYNC_VALUE)

  private val UPDATE_TYPE_VALUE = EventFields.Enum("value", UpdateMethod::class.java) { it.name.lowercase() }
  private val UPDATE_TYPE = GROUP.registerVarargEvent("update.type", UPDATE_TYPE_VALUE)

  private val SAVE_POLICY_VALUE = EventFields.Enum("value", GitSaveChangesPolicy::class.java) { it.name.lowercase() }
  private val SAVE_POLICY = GROUP.registerVarargEvent("save.policy", SAVE_POLICY_VALUE)

  private val PUSH_AUTO_UPDATE = GROUP.registerVarargEvent("push.autoupdate", EventFields.Enabled)

  private val WARN_CRLF = GROUP.registerVarargEvent("warn.about.crlf", EventFields.Enabled)
  private val WARN_DETACHED = GROUP.registerVarargEvent("warn.about.detached", EventFields.Enabled)
  private val WARN_LARGE_FILES = GROUP.registerVarargEvent("warn.about.large.files", EventFields.Enabled)
  private val WARN_BAD_FILE_NAMES = GROUP.registerVarargEvent("warn.about.bad.file.names", EventFields.Enabled)

  private val STAGING_AREA = GROUP.registerVarargEvent("staging.area.enabled", EventFields.Enabled)

  private val TYPE = EventFields.Enum("type", GitVersion.Type::class.java) { it.name }
  private val EXECUTABLE = GROUP.registerVarargEvent("executable", EventFields.Version, TYPE)

  private val COMMON_LOCAL_BRANCHES = EventFields.RoundedInt("common_local_branches")
  private val COMMON_REMOTE_BRANCHES = EventFields.RoundedInt("common_remote_branches")
  private val COMMON_BRANCHES_COUNT_EVENT = GROUP.registerVarargEvent("common_branches_count",
                                                                      COMMON_LOCAL_BRANCHES, COMMON_REMOTE_BRANCHES)

  private val REPO_ID = EventFields.AnonymizedField("repository_id")

  private val COMMITERS_LAST_MONTH = RoundedUserCountEventField("last_month")
  private val COMMITERS_HALF_YEAR = RoundedUserCountEventField("last3_month")
  private val COMMITERS_LAST_YEAR = RoundedUserCountEventField("last_year")

  private val REMOTES_AVAILABILITY = EventFields.EnumList<RepositoryAvailability>("remotes_availability")

  private val LOCAL_BRANCHES = EventFields.RoundedInt("local_branches")
  private val REMOTE_BRANCHES = EventFields.RoundedInt("remote_branches")
  private val RECENT_CHECKOUT_BRANCHES = EventFields.RoundedInt("recent_checkout_branches")
  private val REMOTES = EventFields.RoundedInt("remotes")
  private val IS_WORKTREE_USED = EventFields.Boolean("is_worktree_used")

  private val FS_MONITOR = EventFields.Enum<FsMonitor>("fs_monitor")
  private val remoteTypes = setOf("github", "gitlab", "bitbucket", "gitee",
                                  "github_custom", "gitlab_custom", "bitbucket_custom", "gitee_custom",
                                  "other")

  private val remoteTypesEventIds = remoteTypes.map {
    EventFields.Int("remote_$it")
  }

  private val REPOSITORY = GROUP.registerVarargEvent("repository",
                                                     REPO_ID,
                                                     LOCAL_BRANCHES,
                                                     REMOTE_BRANCHES,
                                                     RECENT_CHECKOUT_BRANCHES,
                                                     REMOTES,
                                                     IS_WORKTREE_USED,
                                                     FS_MONITOR,
                                                     COMMITERS_LAST_MONTH,
                                                     COMMITERS_HALF_YEAR,
                                                     COMMITERS_LAST_YEAR,
                                                     REMOTES_AVAILABILITY,
                                                     *remoteTypesEventIds.toTypedArray()
  )

  private val TEMPLATES_COUNT = EventFields.Int("count")
  private val TEMPLATES_MULTIPLE_ROOTS = EventFields.Boolean("multiple_root")
  private val COMMIT_TEMPLATE = GROUP.registerVarargEvent("commit_template", TEMPLATES_COUNT, TEMPLATES_MULTIPLE_ROOTS)

  private val SHOW_GIT_BRANCHES_IN_LOG = GROUP.registerVarargEvent("showGitBranchesInLog", EventFields.Enabled)
  private val UPDATE_BRANCH_FILTERS_ON_SELECTION = GROUP.registerVarargEvent("updateBranchesFilterInLogOnSelection", EventFields.Enabled)

  private val MAX_LOCAL_BRANCHES = EventFields.RoundedInt("max_local_branches")
  private val SHOW_RECENT_BRANCHES = GROUP.registerVarargEvent("showRecentBranches", EventFields.Enabled, MAX_LOCAL_BRANCHES)

  private val FILTER_BY_ACTION_IN_POPUP = GROUP.registerVarargEvent("filterByActionInPopup", EventFields.Enabled)
  private val FILTER_BY_REPOSITORY_IN_POPUP = GROUP.registerVarargEvent("filterByRepositoryInPopup", EventFields.Enabled)

  private val ALL_IDE_CONFIG_NAMES = listOf(
    ".fleet",
    ".idea",
    ".project",
    ".settings",
    ".vscode",
  )
  private val IDE_CONFIG_NAME = EventFields.String("name", ALL_IDE_CONFIG_NAMES)
  private val IDE_CONFIG_STATUS = EventFields.Enum("status", ConfigStatus::class.java)
  private val SHARED_IDE_CONFIG = GROUP.registerVarargEvent("ide.config", IDE_CONFIG_NAME, IDE_CONFIG_STATUS)

  private fun getRemoteServerType(remote: GitRemote): String {
    val hosts = remote.urls.map(URLUtil::parseHostFromSshUrl).distinct()

    if (hosts.contains("github.com")) return "github"
    if (hosts.contains("gitlab.com")) return "gitlab"
    if (hosts.contains("bitbucket.org")) return "bitbucket"
    if (hosts.contains("gitee.com")) return "gitee"

    if (remote.urls.any { it.contains("github") }) return "github_custom"
    if (remote.urls.any { it.contains("gitlab") }) return "gitlab_custom"
    if (remote.urls.any { it.contains("bitbucket") }) return "bitbucket_custom"
    if (remote.urls.any { it.contains("gitee") }) return "gitee_custom"

    return "other"
  }
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
    if (dotGit.isRegularFile()) return true

    val worktreesPath = repositoryFiles.worktreesDirFile.toPath()
    if (!worktreesPath.exists()) return false
    if (!worktreesPath.isDirectory()) return false

    Files.list(worktreesPath).count() > 0
  }
  catch (e: Exception) {
    if (e is ControlFlowException) throw e
    false
  }
}

private fun getConfigFileStatus(repository: GitRepository, configDirName: String): ConfigStatus? {
  return try {
    val rootPath = repository.root.toNioPath()
    val configDir = rootPath.resolve(configDirName)
    if (!configDir.exists() || !configDir.isDirectory()) {
      return null
    }

    val filePath = VcsUtil.getFilePath(configDir, true)

    val status = getStatus(repository.project, repository.root, listOf(filePath), false, true, true)
    val fileStatus = status.singleOrNull() ?: return ConfigStatus.SHARED
    if (fileStatus.isIgnored() && fileStatus.path.path == filePath.path) { // GitFileStatus has invalid FilePath.isDirectory values
      return ConfigStatus.IGNORED
    }
    return ConfigStatus.SHARED
  }
  catch (e: Exception) {
    if (e is ControlFlowException) throw e
    null
  }
}

internal enum class ConfigStatus {
  IGNORED,
  SHARED
}

internal enum class FsMonitor { NONE, BUILTIN, EXTERNAL_FS_MONITOR }

private fun GitRepository.detectFsMonitor(): FsMonitor {
  try {
    val useBuiltIn = GitConfigUtil.getBooleanValue(GitConfigUtil.getValue(project, root, "core.useBuiltinFSMonitor")) == true
    if (useBuiltIn) return FsMonitor.BUILTIN

    val fsMonitorHook = GitConfigUtil.getValue(project, root, "core.fsmonitor")
    if (fsMonitorHook != null && GitConfigUtil.getBooleanValue(fsMonitorHook) != false) {
      return FsMonitor.EXTERNAL_FS_MONITOR
    }
  }
  catch (_: VcsException) {
  }

  return FsMonitor.NONE
}

private data class RoundedUserCountEventField(
  override val name: String,
  @NonNls override val description: String? = null,
) : PrimitiveEventField<Int>() {

  override val validationRule: List<String>
    get() = listOf("{regexp#integer}")

  override fun addData(fuData: FeatureUsageData, value: Int) {
    val number = if (value in 0..10) {
      value
    }
    else {
      StatisticsUtil.roundToPowerOfTwo(value)
    }
    fuData.addData(name, number)
  }
}