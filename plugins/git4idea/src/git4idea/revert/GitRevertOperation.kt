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
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsFullCommitDetails
import git4idea.GitApplyChangesProcess
import git4idea.commands.Git
import git4idea.commands.GitCommandResult
import git4idea.commands.GitLineHandlerListener
import git4idea.repo.GitRepository

/**
 * Commits should be provided in the "UI" order, i.e. as if `git log --date-order` is called, i.e. in reverse-chronological order.
 */
class GitRevertOperation(private val project: Project,
                         private val commits: List<VcsFullCommitDetails>,
                         private val autoCommit: Boolean) {
  private val git = Git.getInstance()

  fun execute() {
    GitApplyChangesProcess(project, commits, autoCommit, "revert", "reverted",
                           command = { repository, hash, autoCommit, listeners ->
                             doRevert(autoCommit, repository, hash, listeners)
                           },
                           emptyCommitDetector = { result -> result.outputAsJoinedString.contains("nothing to commit") },
                           defaultCommitMessageGenerator = { commit ->
                             """
                             Revert "${commit.subject}"

                             This reverts commit ${commit.id.toShortString()}""".trimIndent()
                           },
                           preserveCommitMetadata = false).execute()
  }

  private fun doRevert(autoCommit: Boolean,
                       repository: GitRepository,
                       hash: Hash,
                       listeners: List<GitLineHandlerListener>): GitCommandResult {
    return git.revert(repository, hash.asString(), autoCommit, *listeners.toTypedArray())
  }
}