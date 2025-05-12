// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.messages.Topic
import com.intellij.vcs.git.shared.branch.calcTooltip
import git4idea.GitTag
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchSyncStatus
import git4idea.branch.GitBranchUtil
import git4idea.i18n.GitBundle
import git4idea.repo.GitRefUtil
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitCurrentBranchPresenter.Companion.PRESENTATION_UPDATED
import git4idea.ui.toolbar.GitToolbarWidgetAction
import icons.DvcsImplIcons
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * Supplies a branch presentation to [git4idea.ui.toolbar.GitToolbarWidgetAction]
 */
interface GitCurrentBranchPresenter {
  companion object {
    private val EP_NAME = ExtensionPointName<GitCurrentBranchPresenter>("Git4Idea.gitCurrentBranchPresenter")

    /**
     * Notifies when new value for [GitCurrentBranchPresenter.getPresentation] is available and should be polled.
     */
    @ApiStatus.Experimental
    val PRESENTATION_UPDATED: Topic<PresentationUpdatedListener> = Topic(PresentationUpdatedListener::class.java, Topic.BroadcastDirection.NONE)

    fun getPresentation(repository: GitRepository): Presentation {
      return EP_NAME.extensionList.firstNotNullOfOrNull { it.getPresentation(repository) } ?: getDefaultPresentation(repository)
    }
  }

  /**
   * Consider notifying [PRESENTATION_UPDATED] when the presentation should be updated.
   *
   * @return a customized presentation for the git widget on the main toolbar or null if it is not applicable .
   */
  fun getPresentation(repository: GitRepository): Presentation?

  interface Presentation {
    val icon: Icon?
    val text: @Nls String
    val description: @Nls String?
    val syncStatus: GitBranchSyncStatus
  }

  @ApiStatus.Experimental
  fun interface PresentationUpdatedListener {
    fun presentationUpdated()
  }

  @ApiStatus.Internal
  data class PresentationData(
    override val icon: Icon?,
    override val text: @Nls String,
    override val description: @Nls String?,
    override val syncStatus: GitBranchSyncStatus = GitBranchSyncStatus.SYNCED,
  ) : Presentation
}

private fun getDefaultPresentation(repository: GitRepository): GitCurrentBranchPresenter.Presentation {
  return GitCurrentBranchPresenter.PresentationData(
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

private fun GitRepository.calcIcon(): Icon? {
  if (state == Repository.State.NORMAL) {
    return null
  }
  if (state == Repository.State.DETACHED && GitRefUtil.getCurrentReference(this) is GitTag) {
    return DvcsImplIcons.BranchLabel
  }
  return AllIcons.General.Warning
}

private fun GitRepository.calcTooltip(): @NlsContexts.Tooltip String? {
  val repoInfo = info
  return when {
    repoInfo.state == Repository.State.DETACHED -> GitBundle.message("git.status.bar.widget.tooltip.detached")
    repoInfo.state == Repository.State.REBASING -> GitBundle.message("git.status.bar.widget.tooltip.rebasing")
    repoInfo.currentBranch != null -> {
      val htmlBuilder = HtmlBuilder()
      var message = DvcsBundle.message("tooltip.branch.widget.vcs.branch.name.text", GitVcs.DISPLAY_NAME.get(), repoInfo.currentBranch.name)
      htmlBuilder.append(message)
      if (!GitUtil.justOneGitRepository(project)) {
        htmlBuilder.br()
          .append(DvcsBundle.message("tooltip.branch.widget.root.name.text", root.name))
      }


      val incomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(project)
      val incomingOutgoingState = incomingOutgoingManager.getIncomingOutgoingState(this, repoInfo.currentBranch)
      val incomingOutgoingTooltip = incomingOutgoingState.calcTooltip()
      if (incomingOutgoingTooltip != null) {
        htmlBuilder.br()
        htmlBuilder.appendRaw(incomingOutgoingTooltip)
      }

      htmlBuilder.toString()
    }
    else -> null
  }
}