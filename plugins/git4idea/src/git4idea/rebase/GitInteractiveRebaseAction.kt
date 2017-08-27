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

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import git4idea.branch.GitRebaseParams

class GitInteractiveRebaseAction : GitCommitEditingAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    prohibitRebaseDuringRebase(e, "rebase")
  }

  override fun actionPerformed(e: AnActionEvent) {
    super.actionPerformed(e)

    val commit = getSelectedCommit(e)
    val project = e.project!!
    val repository = getRepository(e)

    object : Task.Backgroundable(project, "Preparing to rebase") {
      override fun run(indicator: ProgressIndicator) {
        val params = GitRebaseParams.editCommits(commit.parents.first().asString(), null, false)
        GitRebaseUtils.rebase(project, listOf(repository), params, indicator);
      }
    }.queue()
  }

  override fun getFailureTitle() = "Couldn't Start Rebase"
}