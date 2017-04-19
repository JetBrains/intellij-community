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
package git4idea

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.merge.GitConflictResolver

/**
 * Applies the given Git operation (e.g. cherry-pick or revert) to the current working tree,
 * waits for the [ChangeListManager] update, shows the commit dialog and removes the changelist after commit,
 * if the commit was successful.
 */
class GitApplyChangesProcess {

  class ConflictResolver(project: Project,
                         git: Git,
                         root: VirtualFile,
                         commitHash: String,
                         commitAuthor: String,
                         commitMessage: String) : GitConflictResolver(project, git, setOf(root),
                                                                      makeParams(commitHash, commitAuthor, commitMessage)) {
    override fun notifyUnresolvedRemain() {
      // we show a [possibly] compound notification after applying all commits.
    }
  }
}

private fun makeParams(commitHash: String, commitAuthor: String, commitMessage: String): GitConflictResolver.Params {
  val params = GitConflictResolver.Params()
  params.setErrorNotificationTitle("Cherry-picked with conflicts")
  params.setMergeDialogCustomizer(MergeDialogCustomizer(commitHash, commitAuthor, commitMessage))
  return params
}

private class MergeDialogCustomizer(private val commitHash: String,
                                    private val commitAuthor: String,
                                    private val commitMessage: String) : MergeDialogCustomizer() {

  override fun getMultipleFileMergeDescription(files: Collection<VirtualFile>) =
    "<html>Conflicts during cherry-picking commit <code>$commitHash</code> " +
    "made by $commitAuthor<br/><code>\"$commitMessage\"</code></html>"

  override fun getLeftPanelTitle(file: VirtualFile) = "Local changes"

  override fun getRightPanelTitle(file: VirtualFile, revisionNumber: VcsRevisionNumber?) =
    "<html>Changes from cherry-pick <code>$commitHash</code>"
}
