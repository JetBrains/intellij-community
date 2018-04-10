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

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.text.StringUtil.pluralize
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.util.VcsLogUtil.MAX_SELECTED_COMMITS
import com.intellij.vcs.log.util.VcsLogUtil.collectFirstPack
import git4idea.GitUtil.getRepositoryManager
import git4idea.config.GitVcsSettings

class GitRevertAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)

    val project = e.project
    val log = e.getData(VcsLogDataKeys.VCS_LOG)
    if (project == null || log == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val repositoryManager = getRepositoryManager(project)

    val commits = collectFirstPack(log.selectedShortDetails, MAX_SELECTED_COMMITS)
    // commits from mixed roots
    if (commits.any { repositoryManager.getRepositoryForRootQuick(it.root) == null }) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    // reverting merge commit is not allowed
    if (commits.any { it.parents.size > 1 }) {
      e.presentation.isVisible = true
      e.presentation.isEnabled = false
      e.presentation.description = "Reverting merge commits is not allowed"
      return
    }

    e.presentation.isEnabledAndVisible = true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val log = e.getRequiredData(VcsLogDataKeys.VCS_LOG)
    val repositoryManager = getRepositoryManager(project)

    log.requestSelectedDetails rsd@ { commits ->
      if (commits.any { repositoryManager.getRepositoryForRootQuick(it.root) == null }) return@rsd
      if (commits.any { it.parents.size > 1 }) return@rsd

      object : Task.Backgroundable(project, "Reverting ${pluralize("commit", commits.size)}") {
       override fun run(indicator: ProgressIndicator) {
         GitRevertOperation(project, commits, GitVcsSettings.getInstance(project).isAutoCommitOnRevert).execute()
       }
      }.queue()
    }
  }
}
