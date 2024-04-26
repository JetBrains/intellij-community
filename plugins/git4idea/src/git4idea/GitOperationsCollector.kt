// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import git4idea.commands.GitCommandResult
import git4idea.push.GitPushRepoResult

object GitOperationsCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP: EventLogGroup = EventLogGroup("git.operations", 1)

  private val IS_AUTHENTICATION_FAILED = EventFields.Boolean("is_authentication_failed")

  private val PUSHED_COMMITS_COUNT = EventFields.RoundedInt("pushed_commits_count")
  private val PUSH_RESULT = EventFields.Enum<GitPushRepoResult.Type>("push_result")
  private val PUSH_ACTIVITY = GROUP.registerIdeActivity("push",
                                                        finishEventAdditionalFields = arrayOf(PUSHED_COMMITS_COUNT,
                                                                                              PUSH_RESULT,
                                                                                              IS_AUTHENTICATION_FAILED))

  @JvmStatic
  fun startLogPush(project: Project): StructuredIdeActivity {
    return PUSH_ACTIVITY.started(project)
  }

  @JvmStatic
  fun endLogPush(activity: StructuredIdeActivity, commandResult: GitCommandResult?, pushRepoResult: GitPushRepoResult?) {
    activity.finished {
      listOfNotNull(pushRepoResult?.let { PUSHED_COMMITS_COUNT with it.numberOfPushedCommits },
                    pushRepoResult?.let { PUSH_RESULT with it.type },
                    commandResult?.let { IS_AUTHENTICATION_FAILED with it.isAuthenticationFailed }
      )
    }
  }
}
