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
package git4idea.rebase

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.branch.GitRebaseParams
import git4idea.rebase.GitRebaseEntry.Action.pick
import git4idea.rebase.GitRebaseEntry.Action.reword
import git4idea.repo.GitRepository

class GitRewordOperation(private val repository: GitRepository,
                         private val commit: VcsCommitMetadata,
                         private val newMessage: String) {
  private val LOG = logger<GitRewordOperation>()

  fun execute() {
    val rebaseEditor = GitAutomaticRebaseEditor(repository.project, commit.root,
                                                entriesEditor = { list -> injectRewordAction(list) },
                                                plainTextEditor = { editorText -> supplyNewMessage(editorText) })

    val params = GitRebaseParams.editCommits(commit.parents.first().asString(), rebaseEditor, true)
    val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
    val spec = GitRebaseSpec.forNewRebase(repository.project, params, listOf(repository), indicator)
    GitRebaseProcess(repository.project, spec, null).rebase()
  }

  private fun injectRewordAction(list: List<GitRebaseEntry>): List<GitRebaseEntry> {
    return list.map({ entry ->
      if (entry.action == pick && commit.id.asString().startsWith(entry.commit))
        GitRebaseEntry(reword, entry.commit, entry.subject)
      else entry
    })
  }

  private fun supplyNewMessage(editorText: String): String {
    if (editorText.startsWith(commit.fullMessage)) { // there are comments after the proposed message
      return newMessage
    }
    else {
      throw IllegalStateException("Unexpected editor content: $editorText")
    }
  }
}