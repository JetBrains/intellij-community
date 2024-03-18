// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.branch.GitBranchSyncStatus
import git4idea.branch.GitBranchUtil
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.toolbar.GitToolbarWidgetAction
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * Supplies a branch presentation to [git4idea.ui.toolbar.GitToolbarWidgetAction]
 */
@ApiStatus.Internal
interface GitCurrentBranchPresenter {
  companion object {
    private val EP_NAME = ExtensionPointName<GitCurrentBranchPresenter>("Git4Idea.gitCurrentBranchPresenter")

    fun getPresentation(repository: GitRepository): Presentation {
      return EP_NAME.extensionList.firstNotNullOfOrNull { it.getPresentation(repository) } ?: getDefaultPresentation(repository)
    }
  }

  fun getPresentation(repository: GitRepository): Presentation?

  data class Presentation(
    val icon: Icon?,
    val text: @Nls String,
    val description: @Nls String?,
    val syncStatus: GitBranchSyncStatus = GitBranchSyncStatus.SYNCED
  )
}

private fun getDefaultPresentation(repository: GitRepository): GitCurrentBranchPresenter.Presentation {
  return GitCurrentBranchPresenter.Presentation(
    repository.calcIcon(),
    calcText(repository),
    repository.calcTooltip(),
    GitBranchSyncStatus.calcForCurrentBranch(repository)
  )
}

private fun calcText(repository: GitRepository): @NlsSafe String =
  StringUtil.escapeMnemonics(GitBranchUtil.getDisplayableBranchText(repository) { branchName ->
    GitBranchPopupActions.truncateBranchName(repository.project, branchName,
                                             GitToolbarWidgetAction.BRANCH_NAME_MAX_LENGTH,
                                             GitBranchPopupActions.BRANCH_NAME_SUFFIX_LENGTH,
                                             GitBranchPopupActions.BRANCH_NAME_LENGTH_DELTA)
  })

private fun GitRepository.calcIcon(): Icon? = if (state != Repository.State.NORMAL) AllIcons.General.Warning else null

private fun GitRepository.calcTooltip(): @NlsContexts.Tooltip String {
  if (state == Repository.State.DETACHED) {
    return GitBundle.message("git.status.bar.widget.tooltip.detached")
  }

  var message = DvcsBundle.message("tooltip.branch.widget.vcs.branch.name.text", GitVcs.DISPLAY_NAME.get(),
                                   GitBranchUtil.getBranchNameOrRev(this))
  if (!GitUtil.justOneGitRepository(project)) {
    message += "\n"
    message += DvcsBundle.message("tooltip.branch.widget.root.name.text", root.name)
  }
  return message
}