// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.layout.*
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepository
import git4idea.util.GitUIUtil
import javax.swing.JComponent

class GitSelectRootDialog(project: Project,
                          title: String,
                          private val description: String,
                          roots: Collection<GitRepository>,
                          defaultRoot: GitRepository?)
  : DialogWrapper(project, true) {

  private val rootComboBox: ComboBox<GitRepository> = ComboBox()

  init {
    roots.forEach { rootComboBox.addItem(it) }
    rootComboBox.selectedItem = defaultRoot ?: guessCurrentRepository(project, roots)
    rootComboBox.renderer = SimpleListCellRenderer.create("(invalid)", GitRepository::getPresentableUrl)

    setTitle(title)
    setOKButtonText(title)
    init()
  }

  private fun guessCurrentRepository(project: Project, roots: Collection<GitRepository>): GitRepository {
    val repository = GitBranchUtil.getCurrentRepository(project)
    if (repository != null && roots.contains(repository)) return repository
    return roots.first()
  }

  override fun createCenterPanel(): JComponent? {
    return panel {
      row {
        label(description)
      }
      row("Git Root:") {
        rootComboBox()
      }
    }
  }

  override fun getPreferredFocusedComponent(): JComponent? = rootComboBox

  fun selectRoot(): GitRepository? {
    return if (showAndGet()) rootComboBox.selectedItem as GitRepository? else null
  }
}