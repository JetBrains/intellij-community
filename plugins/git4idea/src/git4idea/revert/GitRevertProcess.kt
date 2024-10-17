/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.revert

import com.intellij.openapi.project.Project
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsFullCommitDetails
import git4idea.GitActivity
import git4idea.GitApplyChangesProcess
import git4idea.actions.GitAbortOperationAction
import git4idea.commands.Git
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandlerListener
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import org.jetbrains.annotations.NonNls

/**
 * Commits should be provided in the "UI" order, i.e. as if `git log --date-order` is called, i.e. in reverse-chronological order.
 */
internal class GitRevertProcess(
  project: Project,
  commits: List<VcsFullCommitDetails>,
) : GitApplyChangesProcess(
  project = project,
  commits = commits,
  operationName = GitBundle.message("revert.operation.name"),
  appliedWord = GitBundle.message("revert.operation.applied"),
  abortCommand = GitAbortOperationAction.Revert(),
  preserveCommitMetadata = false,
  activityName = GitBundle.message("activity.name.revert"),
  activityId = GitActivity.Revert
) {
  private val git = Git.getInstance()

  override fun cleanupBeforeCommit(repository: GitRepository) { }

  override fun generateDefaultMessage(repository: GitRepository, commit: VcsCommitMetadata): @NonNls String =
    "Revert \"${commit.subject}\"\n\nThis reverts commit ${commit.id.toShortString()}"

  override fun applyChanges(repository: GitRepository, commit: VcsCommitMetadata, listeners: List<GitLineHandlerListener>): GitCommandResult {
    return git.revert(repository, commit.id.asString(), AUTO_COMMIT, *listeners.toTypedArray<GitLineHandlerListener>())
  }

  override fun isEmptyCommit(result: GitCommandResult): Boolean {
    val stdout = result.outputAsJoinedString
    return stdout.contains("nothing to commit") ||
           stdout.contains("nothing added to commit but untracked files present");
  }

  companion object {
    private const val AUTO_COMMIT = true
  }
}