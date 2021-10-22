// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.push

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.components.JBLabel
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlStringUtil
import git4idea.GitBranch
import git4idea.GitUtil
import git4idea.config.UpdateMethod
import git4idea.i18n.GitBundle
import git4idea.i18n.GitBundleExtensions.html
import git4idea.repo.GitRepository
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.*

class GitRejectedPushUpdateDialog(
  private val myProject: Project,
  private val myRepositories: Collection<GitRepository>,
  initialSettings: PushUpdateSettings,
  private val myRebaseOverMergeProblemDetected: Boolean
) : DialogWrapper(myProject) {
  private val myUpdateAllRoots: JCheckBox
  private val myRebaseAction: RebaseAction
  private val myMergeAction: MergeAction
  private val myAutoUpdateInFuture: JCheckBox

  init {
    myUpdateAllRoots = JCheckBox(GitBundle.message("push.rejected.update.not.rejected.repositories.as.well.checkbox"),
    initialSettings.shouldUpdateAllRoots())
    myAutoUpdateInFuture = JCheckBox(html("push.rejected.remember.checkbox"))
    myMergeAction = MergeAction()
    myRebaseAction = RebaseAction()
    setDefaultAndFocusedActions(initialSettings.updateMethod)
    init()
    title = GitBundle.message("push.rejected.dialog.title")
  }

  private fun setDefaultAndFocusedActions(updateMethod: UpdateMethod?) {
    val defaultAction: Action
    val focusedAction: Action
    if (myRebaseOverMergeProblemDetected) {
      defaultAction = myMergeAction
      focusedAction = cancelAction
    }
    else if (updateMethod == UpdateMethod.REBASE) {
      defaultAction = myRebaseAction
      focusedAction = myMergeAction
    }
    else {
      defaultAction = myMergeAction
      focusedAction = myRebaseAction
    }
    defaultAction.putValue(DEFAULT_ACTION, java.lang.Boolean.TRUE)
    focusedAction.putValue(FOCUSED_ACTION, java.lang.Boolean.TRUE)
  }

  override fun createCenterPanel(): JComponent? {
    val desc = JBLabel(XmlStringUtil.wrapInHtml(makeDescription()!!))
    val options = JPanel(BorderLayout())
    if (!myRebaseOverMergeProblemDetected) {
      options.add(myAutoUpdateInFuture, BorderLayout.SOUTH)
    }
    if (!GitUtil.justOneGitRepository(myProject)) {
      options.add(myUpdateAllRoots)
    }
    val GAP = 15
    val rootPanel = JPanel(BorderLayout(GAP, GAP))
    rootPanel.add(desc)
    rootPanel.add(options, BorderLayout.SOUTH)
    val iconLabel = JLabel(if (myRebaseOverMergeProblemDetected) UIUtil.getWarningIcon() else UIUtil.getQuestionIcon())
    rootPanel.add(iconLabel, BorderLayout.WEST)
    return rootPanel
  }

  override fun getHelpId(): String? {
    return "reference.VersionControl.Git.UpdateOnRejectedPushDialog"
  }

  private fun makeDescription(): @NlsContexts.Label String? {
    return if (GitUtil.justOneGitRepository(myProject)) {
      assert(!myRepositories.isEmpty()) { "repositories are empty" }
      val repository = myRepositories.iterator().next()
      val currentBranch = getCurrentBranch(repository).name
      HtmlBuilder()
        .appendRaw(GitBundle.message("push.rejected.only.one.git.repo", HtmlChunk.text(currentBranch).code())).br()
        .appendRaw(descriptionEnding())
        .toString()
    }
    else if (myRepositories.size == 1) {  // there are more than 1 repositories in the project, but only one was rejected
      val repository = ContainerUtil.getFirstItem(myRepositories)
      val currentBranch = getCurrentBranch(repository).name
      HtmlBuilder()
        .appendRaw(
          GitBundle.message(
            "push.rejected.specific.repo",
            HtmlChunk.text(currentBranch).code(),
            HtmlChunk.text(repository.presentableUrl).code()
          )
        ).br()
        .appendRaw(descriptionEnding())
        .toString()
    }
    else {  // several repositories rejected the push
      val currentBranches = currentBranches
      val description = HtmlBuilder()
      if (allBranchesHaveTheSameName(currentBranches)) {
        val branchName = ContainerUtil.getFirstItem(currentBranches.values).name
        description.appendRaw(GitBundle.message("push.rejected.many.repos.single.branch", HtmlChunk.text(branchName).code())).br()
        for (repository in DvcsUtil.sortRepositories(currentBranches.keys)) {
          description.nbsp(4).append(HtmlChunk.text(repository.presentableUrl).code()).br()
        }
      }
      else {
        description.append(GitBundle.message("push.rejected.many.repos")).br()
        for ((key, value) in currentBranches) {
          val repositoryUrl = key.presentableUrl
          val currentBranch = value.name
          description
            .nbsp(4).appendRaw(GitBundle.message("push.rejected.many.repos.item", HtmlChunk.text(currentBranch).code(),
              HtmlChunk.text(repositoryUrl).code()))
            .br()
        }
      }
      description.br()
        .appendRaw(descriptionEnding())
        .toString()
    }
  }

  private fun descriptionEnding(): @NlsContexts.Label String {
    return if (myRebaseOverMergeProblemDetected) {
      GitBundle.message("push.rejected.merge.needed.with.problem")
    }
    else {
      GitBundle.message("push.rejected.merge.needed")
    }
  }

  private val currentBranches: Map<GitRepository, GitBranch>
    private get() {
      val currentBranches: MutableMap<GitRepository, GitBranch> = HashMap()
      for (repository in myRepositories) {
        currentBranches[repository] = getCurrentBranch(repository)
      }
      return currentBranches
    }

  override fun createActions(): Array<Action> {
    return arrayOf(cancelAction, myMergeAction, myRebaseAction)
  }

  fun shouldUpdateAll(): Boolean {
    return myUpdateAllRoots.isSelected
  }

  fun shouldAutoUpdateInFuture(): Boolean {
    return myAutoUpdateInFuture.isSelected
  }

  @TestOnly
  fun warnsAboutRebaseOverMerge(): Boolean {
    return myRebaseOverMergeProblemDetected
  }

  @get:TestOnly
  val defaultAction: Action
    get() = if (java.lang.Boolean.TRUE == myMergeAction.getValue(DEFAULT_ACTION)) myMergeAction else myRebaseAction

  private inner class MergeAction internal constructor() : AbstractAction(GitBundle.message("push.rejected.merge")) {
    override fun actionPerformed(e: ActionEvent) {
      close(MERGE_EXIT_CODE)
    }
  }

  private inner class RebaseAction internal constructor() : AbstractAction(
    if (myRebaseOverMergeProblemDetected) GitBundle.message("push.rejected.rebase.anyway") else GitBundle.message("push.rejected.rebase")) {
    override fun actionPerformed(e: ActionEvent) {
      close(REBASE_EXIT_CODE)
    }
  }

  companion object {
    const val MERGE_EXIT_CODE = NEXT_USER_EXIT_CODE
    const val REBASE_EXIT_CODE = MERGE_EXIT_CODE + 1
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

    private fun getCurrentBranch(repository: GitRepository): @NlsSafe GitBranch {
      val currentBranch: GitBranch = repository.currentBranch ?: error("Current branch can't be null here. $repository")
      return currentBranch
    }
  }
}