// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.push

import com.intellij.CommonBundle
import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.util.containers.ContainerUtil
import git4idea.GitBranch
import git4idea.GitUtil
import git4idea.config.UpdateMethod
import git4idea.i18n.GitBundle
import git4idea.i18n.GitBundleExtensions.html
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls

class GitRejectedPushUpdateDialog(
  private val project: Project,
  private val repositories: Set<GitRepository>,
  private val initialSettings: PushUpdateSettings,
  private val rebaseOverMergeProblemDetected: Boolean
) {
  @NlsContexts.DialogTitle
  private val title: String = GitBundle.message("push.rejected.dialog.title")

  @Nls
  private val mergeButtonText: String = GitBundle.message("push.rejected.merge")

  @Nls
  private val rebaseButtonText: String =
    if (rebaseOverMergeProblemDetected) GitBundle.message("push.rejected.rebase.anyway")
    else GitBundle.message("push.rejected.rebase")

  @Nls
  private val cancelButtonText: String = CommonBundle.getCancelButtonText()

  private val helpButtonId: String = "reference.VersionControl.Git.UpdateOnRejectedPushDialog"

  private val doNotAsk: DoNotAskOption = createDoNotAsk()

  var shouldAutoUpdateInFuture: Boolean = false
    private set

  fun showAndGet(): PushRejectedExitCode {
    val exitCode: String? = MessageDialogBuilder.Message(title, makeDescription())
      .buttons(mergeButtonText, rebaseButtonText, cancelButtonText)
      .setDefaultAndFocusedActions(initialSettings.updateMethod)
      .help(helpButtonId)
      .doNotAsk(doNotAsk)
      .asWarning()
      .show(project)

    return when (exitCode) {
      mergeButtonText -> PushRejectedExitCode.MERGE
      rebaseButtonText -> PushRejectedExitCode.REBASE
      else -> PushRejectedExitCode.CANCEL
    }
  }

  private fun makeDescription(): @Nls String {
    val description = HtmlBuilder()

    if (GitUtil.justOneGitRepository(project)) {
      assert(repositories.isNotEmpty()) { "repositories are empty" }
      val repository: GitRepository = repositories.single()
      val currentBranch = getCurrentBranch(repository).name
      description.appendRaw(GitBundle.message(
        "push.rejected.only.one.git.repo",
        currentBranch
      )).nbsp()
    }
    else if (repositories.size == 1) {  // there are more than 1 repositories in the project, but only one was rejected
      val repository: GitRepository = ContainerUtil.getFirstItem(repositories)
      val currentBranch = getCurrentBranch(repository).name
      description.appendRaw(GitBundle.message(
        "push.rejected.specific.repo",
        currentBranch,
        DvcsUtil.getShortRepositoryName(repository)
      )).nbsp()
    }
    else {  // several repositories rejected the push
      val currentBranches: Map<GitRepository, GitBranch> = repositories.associateWith { getCurrentBranch(it) }
      if (allBranchesHaveTheSameName(currentBranches)) {
        val branchName = ContainerUtil.getFirstItem(currentBranches.values).name
        description.appendRaw(GitBundle.message(
          "push.rejected.many.repos.single.branch",
          branchName
        )).br()
        for (repository in DvcsUtil.sortRepositories(currentBranches.keys)) {
          description.nbsp(4).append(DvcsUtil.getShortRepositoryName(repository)).br()
        }
      }
      else {
        description.append(GitBundle.message("push.rejected.many.repos")).br()
        for ((repository, branch) in currentBranches) {
          description.nbsp(4).appendRaw(GitBundle.message(
            "push.rejected.many.repos.item",
            branch.name,
            DvcsUtil.getShortRepositoryName(repository)
          )).br()
        }
      }
    }

    return description.appendRaw(descriptionEnding()).toString()
  }

  private fun descriptionEnding(): @NlsContexts.Label String =
    if (rebaseOverMergeProblemDetected) GitBundle.message("push.rejected.merge.needed.with.problem")
    else GitBundle.message("push.rejected.merge.needed")

  private fun getCurrentBranch(repository: GitRepository): @NlsSafe GitBranch {
    return repository.currentBranch ?: error("Current branch can't be null here. $repository")
  }

  private fun allBranchesHaveTheSameName(branches: Map<GitRepository, GitBranch>): Boolean {
    var name: String? = null
    for (branch in branches.values) {
      if (name == null) {
        name = branch.name
      }
      else if (name != branch.name) {
        return false
      }
    }
    return true
  }

  private fun createDoNotAsk() = object : DoNotAskOption.Adapter() {
    override fun getDoNotShowMessage(): String = html("push.rejected.remember.checkbox", CommonBundle.settingsTitle())

    override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
      shouldAutoUpdateInFuture = isSelected
    }
  }

  private fun MessageDialogBuilder.Message.setDefaultAndFocusedActions(
    updateMethod: UpdateMethod
  ): MessageDialogBuilder.Message {
    val defaultAction: String
    val focusedAction: String

    if (rebaseOverMergeProblemDetected) {
      defaultAction = mergeButtonText
      focusedAction = cancelButtonText
    }
    else if (updateMethod == UpdateMethod.REBASE) {
      defaultAction = rebaseButtonText
      focusedAction = mergeButtonText
    }
    else {
      defaultAction = mergeButtonText
      focusedAction = rebaseButtonText
    }

    this.defaultButton(defaultAction)
    this.focusedButton(focusedAction)

    return this
  }

  companion object {
    enum class PushRejectedExitCode(val exitCode: Int) {
      MERGE(0),
      REBASE(1),
      CANCEL(2)
    }
  }
}