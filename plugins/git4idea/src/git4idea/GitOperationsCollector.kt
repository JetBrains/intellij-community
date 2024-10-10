// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import git4idea.commands.GitCommandResult
import git4idea.push.GitPushRepoResult
import git4idea.push.GitPushTarget
import git4idea.push.GitPushTargetType

object GitOperationsCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP: EventLogGroup = EventLogGroup("git.operations", 5)

  internal val UPDATE_FORCE_PUSHED_BRANCH_ACTIVITY = GROUP.registerIdeActivity("update.force.pushed")

  private val IS_AUTHENTICATION_FAILED = EventFields.Boolean("is_authentication_failed")

  private val PUSHED_COMMITS_COUNT = EventFields.RoundedInt("pushed_commits_count")
  private val PUSH_RESULT = EventFields.Enum<GitPushRepoResult.Type>("push_result")
  private val TARGET_TYPE  = EventFields.Enum<GitPushTargetType>("push_target_type")
  private val SET_UPSTREAM  = EventFields.Boolean("push_set_upsteram")
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
  )

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
}
