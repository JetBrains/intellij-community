// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.RoundedIntEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import git4idea.actions.workingTree.GitWorkingTreeDialogData
import git4idea.branch.GitRebaseParams
import git4idea.commands.GitCommandResult
import git4idea.inMemory.rebase.InMemoryRebaseResult
import git4idea.merge.GitMergeOption
import git4idea.pull.GitPullOption
import git4idea.push.GitPushRepoResult
import git4idea.push.GitPushTargetType
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.GitRebaseOption
import git4idea.rebase.interactive.CantRebaseUsingLogException
import git4idea.repo.GitRepository

internal object GitOperationsCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP: EventLogGroup = EventLogGroup(id = "git.operations", version = 9, description = "Git operations")

  internal val UPDATE_FORCE_PUSHED_BRANCH_ACTIVITY = GROUP.registerIdeActivity("update.force.pushed")

  private val IS_AUTHENTICATION_FAILED = EventFields.Boolean("is_authentication_failed")

  private val PUSHED_COMMITS_COUNT = EventFields.RoundedInt("pushed_commits_count")
  private val PUSH_RESULT = EventFields.Enum<GitPushRepoResult.Type>("push_result")
  private val TARGET_TYPE = EventFields.Enum<GitPushTargetType>("push_target_type")
  private val SET_UPSTREAM = EventFields.Boolean("push_set_upsteram")
  private val PUSH_TO_NEW_BRANCH = EventFields.Boolean("push_new_branch")
  private val PUSH_ACTIVITY = GROUP.registerIdeActivity("push",
                                                        finishEventAdditionalFields = arrayOf(PUSHED_COMMITS_COUNT,
                                                                                              PUSH_RESULT,
                                                                                              IS_AUTHENTICATION_FAILED,
                                                                                              TARGET_TYPE,
                                                                                              SET_UPSTREAM,
                                                                                              PUSH_TO_NEW_BRANCH))

  private val EXPECTED_COMMITS_NUMBER = EventFields.Int("expected_commits_number")
  private val ACTUAL_COMMITS_NUMBER = EventFields.Int("actual_commits_number")
  private val INTERACTIVE_REBASE_VIA_LOG_VALIDATION_ERROR = GROUP.registerEvent(
    "rebase.interactive.log.validation_error",
    EXPECTED_COMMITS_NUMBER,
    ACTUAL_COMMITS_NUMBER,
    description = "Validation error during interactive rebase via log",
  )
  private val INTERACTIVE_REBASE_WAS_SUCCESSFUL = EventFields.Boolean("was_successful")
  private val INTERACTIVE_REBASE_ACTIVITY = GROUP.registerIdeActivity("interactive.rebase",
                                                                      finishEventAdditionalFields = arrayOf(
                                                                        INTERACTIVE_REBASE_WAS_SUCCESSFUL))
  private val IN_MEMORY_REBASE_RESULT = EventFields.Enum<InMemoryRebaseResult>("in_memory_rebase_result")
  private val IN_MEMORY_INTERACTIVE_REBASE_ACTIVITY = GROUP.registerIdeActivity("in.memory.interactive.rebase",
                                                                                finishEventAdditionalFields = arrayOf(
                                                                                  IN_MEMORY_REBASE_RESULT))

  private val CANT_REBASE_USING_LOG_REASON = EventFields.Enum<CantRebaseUsingLogException.Reason>("cant_rebase_using_log_reason")
  private val CANT_REBASE_USING_LOG_EVENT = GROUP.registerEvent("cant.rebase.using.log",
                                                                CANT_REBASE_USING_LOG_REASON,
                                                                description = "Rebase using log is not possible")

  val REBASE_ENTRY_TYPE_FIELDS: Map<String, RoundedIntEventField> = GitRebaseEntry.knownActions.associate {
    it.command to EventFields.RoundedInt("${it.command}_entries_count")
  }
  private val REBASE_START_USING_LOG_EVENT = GROUP.registerVarargEvent("rebase.start.using.log",
                                                                       description = "Rebase started using log",
                                                                       *REBASE_ENTRY_TYPE_FIELDS.values.toTypedArray())

  private val WITH_PROVIDED_BRANCH = EventFields.Boolean("with_provided_branch")
  private val WORKING_TREE_CREATION_ACTIVITY =
    GROUP.registerIdeActivity("create.worktree",
                              startEventAdditionalFields = arrayOf(EventFields.ActionPlace, WITH_PROVIDED_BRANCH))

  private val WITH_EXISTING_BRANCH = EventFields.Boolean("with_existing_branch")
  private val WORKTREE_CREATION_DIALOG_EXIT_OK_STAGE =
    WORKING_TREE_CREATION_ACTIVITY.registerStage("worktree.creation.dialog.exited.with.ok",
                                                 arrayOf(WITH_EXISTING_BRANCH))

  private val WORKTREE_PROJECT_WAS_OPENED_AFTER_CREATION_STAGE =
    WORKING_TREE_CREATION_ACTIVITY.registerStage("worktree.project.was.opened.after.creation")

  @JvmStatic
  fun startLogPush(project: Project): StructuredIdeActivity {
    return PUSH_ACTIVITY.started(project)
  }

  @JvmStatic
  fun endLogPush(
    activity: StructuredIdeActivity,
    commandResult: GitCommandResult?,
    pushRepoResult: GitPushRepoResult?,
    targetType: GitPushTargetType?,
    newBranchCreated: Boolean,
    setUpstream: Boolean,
  ) {
    activity.finished {
      listOfNotNull(
        pushRepoResult?.let { PUSHED_COMMITS_COUNT with it.numberOfPushedCommits },
        pushRepoResult?.let { PUSH_RESULT with it.type },
        commandResult?.let { IS_AUTHENTICATION_FAILED with it.isAuthenticationFailed },
        targetType?.let { TARGET_TYPE with it },
        PUSH_TO_NEW_BRANCH with newBranchCreated,
        SET_UPSTREAM with setUpstream,
      )
    }
  }

  fun rebaseViaLogInvalidEntries(project: Project, expectedCommitsNumber: Int, actualCommitsNumber: Int) {
    INTERACTIVE_REBASE_VIA_LOG_VALIDATION_ERROR.log(project, expectedCommitsNumber, actualCommitsNumber)
  }

  fun startInteractiveRebase(project: Project): StructuredIdeActivity {
    return INTERACTIVE_REBASE_ACTIVITY.started(project)
  }

  fun endInteractiveRebase(activity: StructuredIdeActivity, wasSuccessful: Boolean) {
    activity.finished {
      listOf(INTERACTIVE_REBASE_WAS_SUCCESSFUL with wasSuccessful)
    }
  }

  fun startInMemoryInteractiveRebase(project: Project): StructuredIdeActivity {
    return IN_MEMORY_INTERACTIVE_REBASE_ACTIVITY.started(project)
  }

  fun endInMemoryInteractiveRebase(activity: StructuredIdeActivity, result: InMemoryRebaseResult) {
    activity.finished {
      listOf(IN_MEMORY_REBASE_RESULT with result)
    }
  }

  fun logCantRebaseUsingLog(project: Project, reason: CantRebaseUsingLogException.Reason) {
    CANT_REBASE_USING_LOG_EVENT.log(project, reason)
  }

  fun logRebaseStartUsingLog(project: Project, actions: Collection<GitRebaseEntry.Action>) {
    val actionCounts = actions.groupingBy { it.command }.eachCount()

    val eventPairs = actionCounts.entries.mapNotNull { (command, count) ->
      REBASE_ENTRY_TYPE_FIELDS[command]?.with(count)
    }.toTypedArray()

    REBASE_START_USING_LOG_EVENT.log(project, *eventPairs)
  }

  fun logCreateWorktreeActionInvoked(project: Project, place: String, branch: GitReference?): StructuredIdeActivity {
    return WORKING_TREE_CREATION_ACTIVITY.started(project) {
      listOf(EventFields.ActionPlace.with(place), WITH_PROVIDED_BRANCH.with(branch != null))
    }
  }

  fun logWorktreeCreationDialogExitedWithOk(activity: StructuredIdeActivity, workingTreeData: GitWorkingTreeDialogData) {
    activity.stageStarted(WORKTREE_CREATION_DIALOG_EXIT_OK_STAGE) {
      listOf(WITH_EXISTING_BRANCH.with(workingTreeData.newBranchName == null))
    }
  }

  fun logWorktreeProjectOpenedAfterCreation(activity: StructuredIdeActivity) {
    activity.stageStarted(WORKTREE_PROJECT_WAS_OPENED_AFTER_CREATION_STAGE)
  }

  //region Rebase Dialog
  private enum class RebaseUpstreamType { Root, Reference, Commit }

  private val CURRENT_BRANCH = EventFields.Boolean("current_branch", "Current branch")
  private val REBASE_ON_TRACKED_BRANCH = EventFields.Boolean("rebase_on_tracked_branch", "Rebase on tracked branch")

  private val UPSTREAM_TYPE = EventFields.Enum<RebaseUpstreamType>("upstream_type", "Upstream")
  private val OPTION_INTO = EventFields.Boolean("has_onto", "--onto")
  private val OPTION_INTERACTIVE = EventFields.Boolean("option_interactive", "--interactive")
  private val OPTION_REBASE_MERGES = EventFields.Boolean("option_rebase_merges", "--rebase-merges")
  private val OPTION_KEEP_EMPTY = EventFields.Boolean("option_keep_empty", "--keep-empty")
  private val OPTION_UPDATE_REFS = EventFields.Boolean("option_update_refs", "--update-refs")
  private val OPTION_AUTOSQUASH = EventFields.Boolean("option_autosquash", "--autosquash")

  private val REBASE_FROM_DIALOG_EVENT = GROUP.registerVarargEvent("rebase.from.dialog",
                                                                   "Rebase from dialog was started",
                                                                   CURRENT_BRANCH,
                                                                   REBASE_ON_TRACKED_BRANCH,
                                                                   UPSTREAM_TYPE,
                                                                   OPTION_INTO,
                                                                   OPTION_REBASE_MERGES,
                                                                   OPTION_KEEP_EMPTY,
                                                                   OPTION_INTERACTIVE,
                                                                   OPTION_UPDATE_REFS,
                                                                   OPTION_AUTOSQUASH)

  @JvmStatic
  fun logRebaseFromDialog(project: Project, gitRepository: GitRepository, selectedParams: GitRebaseParams) {
    val upstream = selectedParams.upstream

    val rebaseOnTrackedBranch = isRebaseOnTrackedBranch(upstream, gitRepository, selectedParams.branch)
    val upstreamType = getUpstreamType(upstream)
    val options = selectedParams.getSelectedOptions()

    REBASE_FROM_DIALOG_EVENT.log(
      project,
      CURRENT_BRANCH.with(selectedParams.branch == null),
      REBASE_ON_TRACKED_BRANCH.with(rebaseOnTrackedBranch),
      UPSTREAM_TYPE.with(upstreamType),
      OPTION_INTO.with(GitRebaseOption.ONTO in options),
      OPTION_REBASE_MERGES.with(GitRebaseOption.REBASE_MERGES in options),
      OPTION_KEEP_EMPTY.with(GitRebaseOption.KEEP_EMPTY in options),
      OPTION_INTERACTIVE.with(GitRebaseOption.INTERACTIVE in options),
      OPTION_UPDATE_REFS.with(GitRebaseOption.UPDATE_REFS in options),
      OPTION_AUTOSQUASH.with(GitRebaseOption.AUTOSQUASH in options)
    )
  }

  private fun isRebaseOnTrackedBranch(
    upstream: GitRebaseParams.RebaseUpstream,
    gitRepository: GitRepository,
    selectedBranch: String?,
  ): Boolean {
    if (upstream !is GitRebaseParams.RebaseUpstream.Reference) return false

    val branchToBeRebased = (selectedBranch ?: gitRepository.currentBranch?.name) ?: return false
    val trackedBranch = gitRepository.getBranchTrackInfo(branchToBeRebased)?.remoteBranch?.name ?: return false

    return trackedBranch == upstream.ref
  }

  private fun getUpstreamType(upstream: GitRebaseParams.RebaseUpstream): RebaseUpstreamType = when (upstream) {
    is GitRebaseParams.RebaseUpstream.Root -> RebaseUpstreamType.Root
    is GitRebaseParams.RebaseUpstream.Commit -> RebaseUpstreamType.Commit
    is GitRebaseParams.RebaseUpstream.Reference -> RebaseUpstreamType.Reference
  }
  //endregion

  //region Merge/Pull Dialog
  // Merge/Pull common fields
  private val MERGE_PULL_TRACKED_BRANCH = EventFields.Boolean("tracked_branch", "Tracked branch")
  private val MERGE_PULL_OPTION_NO_FF = EventFields.Boolean("option_no_ff", "--no-ff")
  private val MERGE_PULL_OPTION_FF_ONLY = EventFields.Boolean("option_ff_only", "--ff-only")
  private val MERGE_PULL_OPTION_SQUASH = EventFields.Boolean("option_squash", "--squash")
  private val MERGE_PULL_OPTION_NO_COMMIT = EventFields.Boolean("option_no_commit", "--no-commit")
  private val MERGE_PULL_OPTION_NO_VERIFY = EventFields.Boolean("option_no_verify", "--no-verify")
  
  // Merge-specific options
  private val MERGE_OPTION_COMMIT_MESSAGE = EventFields.Boolean("option_commit_message", "-m")
  private val MERGE_OPTION_ALLOW_UNRELATED_HISTORIES = EventFields.Boolean("option_allow_unrelated_histories", "--allow-unrelated-histories")

  private val MERGE_FROM_DIALOG_EVENT = GROUP.registerVarargEvent("merge.from.dialog",
                                                                   "Merge from dialog was started",
                                                                   MERGE_PULL_TRACKED_BRANCH,
                                                                   MERGE_PULL_OPTION_NO_FF,
                                                                   MERGE_PULL_OPTION_FF_ONLY,
                                                                   MERGE_PULL_OPTION_SQUASH,
                                                                   MERGE_PULL_OPTION_NO_COMMIT,
                                                                   MERGE_OPTION_COMMIT_MESSAGE,
                                                                   MERGE_PULL_OPTION_NO_VERIFY,
                                                                   MERGE_OPTION_ALLOW_UNRELATED_HISTORIES)

  // Pull-specific options
  private val PULL_OPTION_REBASE = EventFields.Boolean("option_rebase", "--rebase")

  private val PULL_FROM_DIALOG_EVENT = GROUP.registerVarargEvent("pull.from.dialog",
                                                                  "Pull from dialog was started",
                                                                  MERGE_PULL_TRACKED_BRANCH,
                                                                  PULL_OPTION_REBASE,
                                                                  MERGE_PULL_OPTION_FF_ONLY,
                                                                  MERGE_PULL_OPTION_NO_FF,
                                                                  MERGE_PULL_OPTION_SQUASH,
                                                                  MERGE_PULL_OPTION_NO_COMMIT,
                                                                  MERGE_PULL_OPTION_NO_VERIFY)

  @JvmStatic
  fun logMergeFromDialog(
    project: Project,
    repository: GitRepository?,
    selectedBranch: GitBranch,
    selectedOptions: Collection<GitMergeOption>,
  ) {
    MERGE_FROM_DIALOG_EVENT.log(
      project,
      MERGE_PULL_TRACKED_BRANCH.with(isTargetTrackedBranch(repository, selectedBranch)),
      MERGE_PULL_OPTION_NO_FF.with(GitMergeOption.NO_FF in selectedOptions),
      MERGE_PULL_OPTION_FF_ONLY.with(GitMergeOption.FF_ONLY in selectedOptions),
      MERGE_PULL_OPTION_SQUASH.with(GitMergeOption.SQUASH in selectedOptions),
      MERGE_PULL_OPTION_NO_COMMIT.with(GitMergeOption.NO_COMMIT in selectedOptions),
      MERGE_OPTION_COMMIT_MESSAGE.with(GitMergeOption.COMMIT_MESSAGE in selectedOptions),
      MERGE_PULL_OPTION_NO_VERIFY.with(GitMergeOption.NO_VERIFY in selectedOptions),
      MERGE_OPTION_ALLOW_UNRELATED_HISTORIES.with(GitMergeOption.ALLOW_UNRELATED_HISTORIES in selectedOptions)
    )
  }

  @JvmStatic
  fun logPullFromDialog(
    project: Project,
    repository: GitRepository?,
    selectedBranch: GitBranch,
    selectedOptions: Collection<GitPullOption>,
  ) {
    PULL_FROM_DIALOG_EVENT.log(
      project,
      MERGE_PULL_TRACKED_BRANCH.with(isTargetTrackedBranch(repository, selectedBranch)),
      PULL_OPTION_REBASE.with(GitPullOption.REBASE in selectedOptions),
      MERGE_PULL_OPTION_FF_ONLY.with(GitPullOption.FF_ONLY in selectedOptions),
      MERGE_PULL_OPTION_NO_FF.with(GitPullOption.NO_FF in selectedOptions),
      MERGE_PULL_OPTION_SQUASH.with(GitPullOption.SQUASH in selectedOptions),
      MERGE_PULL_OPTION_NO_COMMIT.with(GitPullOption.NO_COMMIT in selectedOptions),
      MERGE_PULL_OPTION_NO_VERIFY.with(GitPullOption.NO_VERIFY in selectedOptions)
    )
  }

  private fun isTargetTrackedBranch(
    repository: GitRepository?,
    selectedBranch: GitBranch,
  ): Boolean {
    if (repository == null) return false
    val currentBranch = repository.currentBranch ?: return false

    val trackedBranch = repository.getBranchTrackInfo(currentBranch.name)?.remoteBranch ?: return false
    return trackedBranch == selectedBranch
  }
  //endregion
}
