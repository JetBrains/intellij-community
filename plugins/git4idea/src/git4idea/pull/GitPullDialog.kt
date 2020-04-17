// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.pull

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.DvcsUtil.sortRepositories
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.*
import com.intellij.util.TextFieldCompletionProvider
import com.intellij.util.textCompletion.TextCompletionProvider
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.JBUI
import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch
import git4idea.branch.GitBranchPair
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle.message
import git4idea.merge.GitPullDialog
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.update.updateMethodButtonGroup
import java.awt.event.ItemEvent
import javax.swing.JList

class GitPullDialog(private val project: Project) : DialogWrapper(project) {

  private val repositories = sortRepositories(GitRepositoryManager.getInstance(project).repositories)

  private val branches = repositories.associateWith { repository ->
    repository.branches.remoteBranches.groupBy { branch -> branch.remote }
  }

  private val rootsToUpdate = collectUpdateConfig()

  var updateMethod = GitVcsSettings.getInstance(project).updateMethod; private set

  private val repositoriesField: ComboBox<GitRepository> = createReposField()
  private val remotesField: ComboBox<GitRemote> = createRemotesField()
  private val branchesField: TextFieldWithCompletion = createBranchesField()
  private val setAsUpstreamField: JBCheckBox = JBCheckBox(message("pull.dialog.upstream.field.title"))

  val panel = createPanel()

  init {
    val branchName = repositories[0].currentBranch?.name
    if (branchName == null || branchName.isEmpty()) {
      throw IllegalStateException("VCS root is not on branch: ${repositories[0].root}")
    }

    title = message("pull.dialog.title", branchName)

    setOKButtonText(message("pull.button"))

    panel.reset()

    init()
  }

  override fun createCenterPanel() = panel

  fun getUpdateConfig() = rootsToUpdate.mapValues { (_, branchPair) -> GitBranchPair(branchPair.first, branchPair.second!!) }

  fun shouldSetAsUpstream() = setAsUpstreamField.isSelected

  private fun collectUpdateConfig(): MutableMap<GitRepository, Pair<GitLocalBranch, GitRemoteBranch?>> {
    val map = mutableMapOf<GitRepository, Pair<GitLocalBranch, GitRemoteBranch?>>()

    repositories.forEach { repository ->
      val localBranch = repository.currentBranch
      if (localBranch != null) {
        val trackedBranch = localBranch.findTrackedBranch(repository) ?: getMatchingBranch(repository)
        map[repository] = localBranch to trackedBranch
      }
      else {
        LOG.error(IllegalStateException("VCS root is not on branch: ${repository.root}"))
      }
    }

    return map
  }

  private fun getMatchingBranch(repository: GitRepository): GitRemoteBranch? {
    return branches[repository]?.values?.flatten()
      ?.firstOrNull { branch -> branch.nameForRemoteOperations == repository.currentBranchName }
  }

  private fun setBranchToUpdate(repository: GitRepository, branchToUpdateFrom: GitRemoteBranch?) {
    if (branchToUpdateFrom == null) {
      return
    }

    val branchPair = rootsToUpdate[repository]!!

    rootsToUpdate[repository] = Pair(branchPair.first, branchToUpdateFrom)
  }

  private fun getSelectedRepository() = repositoriesField.item

  // UI

  private fun createPanel(): DialogPanel = panel {
    if (repositories.size > 1) row {
      label(message("pull.dialog.git.root"))
      repositoriesField()
    }
    row {
      label(message("pull.dialog.from"))
      remotesField(growPolicy = GrowPolicy.SHORT_TEXT)
      label("/")
      branchesField(growPolicy = GrowPolicy.SHORT_TEXT)
    }
    if (showSetAsUpstreamField()) row {
      setAsUpstreamField()
    }
    updateMethodButtonGroup(
      get = { method -> updateMethod == method },
      set = { selected, method -> if (selected) updateMethod = method }
    )
  }.withBorder(JBUI.Borders.empty(16, 5, 0, 5))

  private fun createReposField(): ComboBox<GitRepository> {
    val field = ComboBox(CollectionComboBoxModel(repositories))

    field.renderer = object : SimpleListCellRenderer<GitRepository>() {
      override fun customize(list: JList<out GitRepository>, value: GitRepository?, index: Int, selected: Boolean, hasFocus: Boolean) {
        text = if (value != null) DvcsUtil.getShortRepositoryName(value) else ""
      }
    }

    field.addItemListener { e ->
      if (e.stateChange == ItemEvent.SELECTED
          && e.item != null) {
        updateRemotesField(e.item as GitRepository)
      }
    }

    return field
  }

  private fun createRemotesField(): ComboBox<GitRemote> {
    val field = ComboBox(CollectionComboBoxModel(ArrayList(repositories[0].remotes)))

    field.renderer = object : SimpleListCellRenderer<GitRemote>() {
      override fun customize(list: JList<out GitRemote>, value: GitRemote?, index: Int, selected: Boolean, hasFocus: Boolean) {
        text = value?.name ?: ""
      }
    }

    return field
  }

  private fun updateRemotesField(repository: GitRepository) {
    remotesField.model = CollectionComboBoxModel(ArrayList(repository.remotes))

    setBranchToUpdate(repository, getMatchingBranch(repository))
  }

  private fun createBranchesField(): TextFieldWithCompletion {
    val repository = getSelectedRepository()
    val matchingBranch = getMatchingBranch(repository)

    setBranchToUpdate(repository, matchingBranch)

    return TextFieldWithCompletion(
      project,
      createBranchesCompletionProvider(),
      matchingBranch?.nameForRemoteOperations ?: "",
      true,
      true,
      false
    )
  }

  private fun getMatchingBranches(repository: GitRepository, input: String): List<GitRemoteBranch> {
    return branches[repository]?.values?.flatten()
             ?.filter { branch -> branch.nameForRemoteOperations.contains(input) }
           ?: emptyList()
  }

  private fun createBranchesCompletionProvider(): TextCompletionProvider {
    return object : TextFieldCompletionProvider() {
      override fun addCompletionVariants(text: String, offset: Int, prefix: String, result: CompletionResultSet) {
        getMatchingBranches(getSelectedRepository(), text)
          .forEach { branch -> result.addElement(LookupElementBuilder.create(branch)) }
      }
    }
  }

  private fun showSetAsUpstreamField() = repositories.any { repo -> repo.currentBranch?.findTrackedBranch(repo) == null }

  companion object {
    val LOG = Logger.getInstance(GitPullDialog::class.java)
  }
}
