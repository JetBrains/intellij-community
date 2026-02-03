// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import git4idea.GitStashUsageCollector
import git4idea.i18n.GitBundle
import git4idea.rebase.log.commitMessageWithLabelAndToolbar
import git4idea.util.GitUIUtil
import javax.swing.JComponent

internal class GitStashDialog(private val project: Project, roots: List<VirtualFile>, defaultRoot: VirtualFile) : DialogWrapper(project, true) {
  private val rootComboBox = ComboBox<VirtualFile>().also { it.toolTipText = GitBundle.message("common.git.root.tooltip") }
  private val currentBranchLabel = JBLabel().also { it.toolTipText = GitBundle.message("common.current.branch.tooltip") }
  private val stashMessageEditor = CommitMessage(project, false, false, true).also {
    it.preferredSize = JBUI.size(400, 60)
    it.toolTipText = GitBundle.message("stash.message.tooltip")
    Disposer.register(disposable, it)
  }
  private val keepIndexCheckBox = JBCheckBox(GitBundle.message("stash.keep.index")).also { it.toolTipText = GitBundle.message("stash.keep.index.tooltip") }

  val selectedRoot: VirtualFile get() = rootComboBox.item
  val message: String get() = stashMessageEditor.text
  val keepIndex: Boolean get() = keepIndexCheckBox.isSelected

  init {
    title = GitBundle.message("stash.title")
    setOKButtonText(GitBundle.message("stash.button"))
    GitUIUtil.setupRootChooser(project, roots, defaultRoot, rootComboBox, currentBranchLabel)
    init()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row(GitBundle.message("common.git.root")) { cell(rootComboBox) }
      row(GitBundle.message("common.current.branch")) { cell(currentBranchLabel) }
      commitMessageWithLabelAndToolbar(stashMessageEditor, GitBundle.message("stash.message"))
      row { cell(keepIndexCheckBox) }
    }
  }

  override fun doOKAction() {
    super.doOKAction()

    GitStashUsageCollector.logStashPushDialog(message.isNotEmpty(), keepIndex)
  }

  override fun dispose() {
    if (message.isNotEmpty()) {
      VcsConfiguration.getInstance(project).saveCommitMessage(message)
    }
    super.dispose()
  }

  override fun getPreferredFocusedComponent(): JComponent = stashMessageEditor.editorField
  override fun getDimensionServiceKey(): String = javaClass.name
  override fun getHelpId(): String = "reference.VersionControl.Git.Stash"
}