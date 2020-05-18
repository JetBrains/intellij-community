// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.TextFieldCompletionProvider
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import git4idea.GitRemoteBranch
import git4idea.branch.GitBranchPair
import git4idea.config.GitVcsSettings
import git4idea.config.UpdateMethod
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.ItemEvent
import java.awt.event.KeyEvent
import javax.swing.*

internal class FixTrackedBranchDialog(private val project: Project) : DialogWrapper(project) {

  private val repositories = DvcsUtil.sortRepositories(GitRepositoryManager.getInstance(project).repositories)

  private val branches = repositories.associateWith { repository ->
    repository.branches.remoteBranches.groupBy { branch -> branch.remote }
  }

  var updateConfig = collectUpdateConfig(); private set

  var updateMethod = GitVcsSettings.getInstance(project).updateMethod; private set

  private val repositoryField = createRepositoryField()
  private val remoteField = createRemoteField()
  private val branchField = createBranchField()
  private val setAsTrackedBranchField = JCheckBox(GitBundle.message("tracked.branch.fix.dialog.set.as.tracked")).apply {
    mnemonic = KeyEvent.VK_S
  }
  private val updateMethodButtonGroup = createUpdateMethodButtonGroup()

  private val panel = createPanel()

  init {
    title = GitBundle.message("tracked.branch.fix.dialog.title")
    setOKButtonText(GitBundle.message("tracked.branch.fix.dialog.ok.button.text"))
    init()
  }

  override fun createCenterPanel() = panel

  override fun getPreferredFocusedComponent() = branchField

  override fun doValidateAll() = validateUpdateConfig()

  fun shouldSetAsTrackedBranch() = setAsTrackedBranchField.isSelected

  private fun collectUpdateConfig(): MutableMap<GitRepository, GitBranchPair> {
    val map = mutableMapOf<GitRepository, GitBranchPair>()

    repositories.forEach { repository ->
      val localBranch = repository.currentBranch
      check(localBranch != null) { "VCS root is not on branch: ${repository.root}" }

      val trackedBranch = localBranch.findTrackedBranch(repository) ?: getBranchMatchingLocal(repository)
      if (trackedBranch != null) {
        map[repository] = GitBranchPair(localBranch, trackedBranch)
      }
    }

    return map
  }

  private fun validateUpdateConfig(): MutableList<ValidationInfo> {
    val validationResult = mutableListOf<ValidationInfo>()

    for (repository in repositories) {
      if (!updateConfig.keys.contains(repository)) {
        validationResult += ValidationInfo(GitBundle.message("tracked.branch.fix.dialog.no.tracked.branch"), branchField)
        repositoryField.item = repository
        break
      }
    }

    return validationResult
  }

  private fun getSelectedRepository() = repositoryField.item

  private fun getMatchingBranch(repository: GitRepository, predicate: (GitRemoteBranch) -> Boolean): GitRemoteBranch? {
    return branches[repository]?.values?.flatten()?.firstOrNull(predicate)
  }

  private fun getBranchMatchingLocal(repository: GitRepository): GitRemoteBranch? {
    return getMatchingBranch(repository) { branch ->
      branch.nameForRemoteOperations == repository.currentBranchName
    }
  }

  private fun getMatchingBranches(repository: GitRepository, input: String): List<GitRemoteBranch> {
    return branches[repository]?.values?.flatten()?.filter { branch ->
      branch.nameForRemoteOperations.contains(input)
    } ?: emptyList()
  }

  private fun setBranchToUpdateFrom(repository: GitRepository, trackedBranch: GitRemoteBranch?) {
    if (trackedBranch == null) {
      return
    }

    val branchPair = updateConfig[repository]
    if (branchPair != null) {
      updateConfig[repository] = GitBranchPair(branchPair.source, trackedBranch)
    }
    else {
      val localBranch = repository.currentBranch
      check(localBranch != null) { "VCS root is not on branch: ${repository.root}" }

      updateConfig[repository] = GitBranchPair(localBranch, trackedBranch)
    }
  }

  private fun createPanel() = JPanel().apply {
    layout = MigLayout(
      LC().insets("0").hideMode(3))

    val showRepositoryField = repositories.size > 1

    add(JLabel(GitBundle.message("pull.dialog.git.root")).apply {
      labelFor = repositoryField
      displayedMnemonic = KeyEvent.VK_G
      isVisible = showRepositoryField
    })
    add(repositoryField.apply { isVisible = showRepositoryField }, CC().spanX(3))

    add(JLabel(GitBundle.message("pull.dialog.from")).apply {
      labelFor = remoteField
      displayedMnemonic = KeyEvent.VK_F
    }, CC().newline())
    add(remoteField, CC())

    add(JLabel("/"), CC())
    add(branchField, CC().pushX().growX().minWidth("${JBUI.scale(250)}px").shrinkX(0f))

    if (showSetAsTrackedField()) {
      add(setAsTrackedBranchField, CC().newline().spanX(4))
    }

    add(updateMethodButtonGroup, CC().newline().spanX(4))
  }

  private fun showSetAsTrackedField() = repositories.any { repo -> repo.currentBranch?.findTrackedBranch(repo) == null }

  private fun createRepositoryField() = ComboBox(CollectionComboBoxModel(repositories)).apply {
    preferredSize = JBDimension(JBUI.scale(90), preferredSize.height, true)
    renderer = SimpleListCellRenderer.create("") { repository ->
      DvcsUtil.getShortRepositoryName(repository)
    }
    addItemListener { e ->
      if (e.stateChange == ItemEvent.SELECTED
          && e.item != null) {
        updateRemoteField(e.item as GitRepository)
      }
    }
  }

  private fun createRemoteField() = ComboBox(MutableCollectionComboBoxModel(repositories[0].remotes.toMutableList())).apply {
    preferredSize = JBDimension(JBUI.scale(90), preferredSize.height, true)
    renderer = SimpleListCellRenderer.create("") { remote ->
      remote.name
    }
    addItemListener { e ->
      if (e.stateChange == ItemEvent.SELECTED
          && e.item != null) {
        updateBranchField()
      }
    }
  }

  private fun createBranchField(): TextFieldWithCompletion {
    return TextFieldWithCompletion(
      project,
      createBranchesCompletionProvider(),
      getBranchMatchingLocal(getSelectedRepository())?.nameForRemoteOperations ?: "",
      true,
      true,
      false
    ).apply {
      size = JBDimension(250, 28)

      setPlaceholder(GitBundle.message("tracked.branch.fix.dialog.branch.placeholder"))

      addFocusListener(object : FocusAdapter() {
        override fun focusLost(e: FocusEvent?) {
          if (text.isNotEmpty()) {
            setBranchToUpdateFrom(getSelectedRepository(), getMatchingBranch(getSelectedRepository()) { branch ->
              branch.nameForRemoteOperations == text
            })
          }
        }
      })
    }
  }

  private fun createBranchesCompletionProvider() = object : TextFieldCompletionProvider() {
    override fun addCompletionVariants(text: String, offset: Int, prefix: String, result: CompletionResultSet) {
      getMatchingBranches(getSelectedRepository(), text)
        .forEach { branch -> result.addElement(LookupElementBuilder.create(branch.nameForRemoteOperations)) }
    }
  }

  private fun createUpdateMethodButtonGroup() = JPanel().apply {
    layout = MigLayout(LC().insets("0"))

    val buttonGroup = ButtonGroup()

    listOf(UpdateMethod.MERGE, UpdateMethod.REBASE).forEach { method ->
      val radioButton = JRadioButton(method.presentation).apply {
        mnemonic = method.name[0].toInt()
        model.isSelected = updateMethod == method
        addActionListener {
          updateMethod = method
        }
      }

      buttonGroup.add(radioButton)
      add(radioButton, CC().newline())
    }
  }


  private fun updateRemoteField(repository: GitRepository) {
    (remoteField.model as MutableCollectionComboBoxModel).update(repository.remotes.toMutableList())
  }

  private fun updateBranchField() {
    branchField.text = getBranchMatchingLocal(getSelectedRepository())?.nameForRemoteOperations ?: ""
  }
}