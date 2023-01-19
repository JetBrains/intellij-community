// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.dvcs.DvcsUtil
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil.*
import com.intellij.ide.ui.laf.darcula.ui.DarculaEditorTextFieldBorder
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.ui.*
import com.intellij.util.TextFieldCompletionProvider
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import git4idea.GitNotificationIdsHolder.Companion.FIX_TRACKED_NOT_ON_BRANCH
import git4idea.GitRemoteBranch
import git4idea.branch.GitBranchPair
import git4idea.config.GitVcsSettings
import git4idea.config.UpdateMethod
import git4idea.i18n.GitBundle
import git4idea.merge.dialog.FlatComboBoxUI
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.ItemEvent
import java.awt.event.KeyEvent
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import javax.swing.ButtonGroup
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JRadioButton

internal class FixTrackedBranchDialog(private val project: Project) : DialogWrapper(project) {

  private val vcsNotifier = project.service<VcsNotifier>()

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
    isVisible = repositories.any { repo -> repo.currentBranch?.findTrackedBranch(repo) == null }
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

  fun shouldSetAsTrackedBranch() = setAsTrackedBranchField.isSelected

  private fun collectUpdateConfig(): MutableMap<GitRepository, GitBranchPair> {
    val map = mutableMapOf<GitRepository, GitBranchPair>()

    val reposNotOnBranch = repositories.filter { it.currentBranch == null }.joinToString { DvcsUtil.getShortRepositoryName(it) }
    if (reposNotOnBranch.isNotEmpty()) {
      val message = HtmlBuilder()
        .append(GitBundle.message("tracked.branch.fix.dialog.not.on.branch.message"))
        .append(HtmlChunk.br())
        .append(reposNotOnBranch)
        .toString()
      vcsNotifier.notifyImportantWarning(FIX_TRACKED_NOT_ON_BRANCH,
                                         GitBundle.message("tracked.branch.fix.dialog.not.on.branch.title"), message)
    }

    for (repository in repositories) {
      val localBranch = repository.currentBranch
      if (localBranch != null) {
        val trackedBranch = localBranch.findTrackedBranch(repository) ?: getBranchMatchingLocal(repository)
        if (trackedBranch != null) {
          map[repository] = GitBranchPair(localBranch, trackedBranch)
        }
      }
    }

    return map
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
      if (localBranch == null) {
        logger<FixTrackedBranchDialog>().warn("VCS root is not on branch: ${repository.root}")
        return
      }

      updateConfig[repository] = GitBranchPair(localBranch, trackedBranch)
    }
  }

  private fun createPanel() = JPanel().apply {
    layout = MigLayout(LC().insets("0").hideMode(3).gridGap("0", "5").noVisualPadding(), AC().grow())

    add(repositoryField, CC().minWidth("125").growX().alignY("top"))
    add(remoteField, CC().minWidth("125").growX().alignY("top"))
    add(branchField, CC().minWidth("250").growX().alignY("top"))
    add(setAsTrackedBranchField, CC().newline().spanX(3).gapTop("7"))
    add(updateMethodButtonGroup, CC().newline().spanX(3))
  }

  private fun createRepositoryField() = ComboBox(CollectionComboBoxModel(repositories)).apply {
    isVisible = showRepositoryField()
    preferredSize = JBDimension(125, 30)
    renderer = SimpleListCellRenderer.create("") { repository ->
      DvcsUtil.getShortRepositoryName(repository)
    }
    setUI(FlatComboBoxUI(border = Insets(1, 1, 1, 0),
                         outerInsets = Insets(BW.get(), BW.get(), BW.get(), 0)))
    addItemListener { e ->
      if (e.stateChange == ItemEvent.SELECTED
          && e.item != null) {
        updateRemoteField(e.item as GitRepository)
      }
    }
  }

  private fun showRepositoryField() = repositories.size > 1

  private fun createRemoteField() = ComboBox(MutableCollectionComboBoxModel(repositories[0].remotes.toMutableList())).apply {
    preferredSize = JBDimension(125, 30)
    renderer = SimpleListCellRenderer.create("") { remote ->
      remote.name
    }
    setUI(FlatComboBoxUI(border = Insets(1, 1, 1, 0),
                         outerInsets = Insets(BW.get(), if (showRepositoryField()) 0 else BW.get(), BW.get(), 0)))
    addItemListener { e ->
      if (e.stateChange == ItemEvent.SELECTED
          && e.item != null) {
        updateBranchField()
      }
    }
  }

  private fun createBranchField(): TextFieldWithCompletion {
    return object : TextFieldWithCompletion(project,
                                            createBranchesCompletionProvider(),
                                            getBranchMatchingLocal(getSelectedRepository())?.nameForRemoteOperations ?: "",
                                            true, true, false) {
      init {
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

      override fun getPreferredSize(): Dimension = JBDimension(super.getPreferredSize().width, JBUI.scale(30), true)

      override fun setupBorder(editor: EditorEx) = editor.setBorder(MyDarculaEditorTextFieldBorder(this, editor))
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
        mnemonic = method.methodName[0].code
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

  private class MyDarculaEditorTextFieldBorder(editorTextField: EditorTextField, editorEx: EditorEx)
    : DarculaEditorTextFieldBorder(editorTextField, editorEx) {

    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
      val editorTextField = ComponentUtil.getParentOfType(EditorTextField::class.java as Class<out EditorTextField?>, c) ?: return

      val r = Rectangle(x, y, width, height)
      val g2 = g.create() as Graphics2D
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)

        JBInsets.removeFrom(r, Insets(1, 0, 1, 0))
        g2.translate(r.x, r.y)

        val lw = lw(g2)
        val bw = BW.get().toFloat()

        val hasFocus = editorTextField.focusTarget.hasFocus()
        if (hasFocus) {
          paintOutlineBorder(g2, r.width, r.height, 0f, true, true, Outline.focus)
        }

        val border = Path2D.Float(Path2D.WIND_EVEN_ODD)
        border.append(Rectangle2D.Float(0f, bw, r.width - bw, r.height - bw * 2), false)
        border.append(Rectangle2D.Float(lw, bw + lw, r.width - lw * 2 - bw, r.height - (bw + lw) * 2), false)
        g2.color = DarculaUIUtil.getOutlineColor(true, hasFocus)
        g2.fill(border)
      }
      finally {
        g2.dispose()
      }
    }
  }
}