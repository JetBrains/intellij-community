// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.layout.*
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

class GitSelectRootDialog(project: Project,
                          @Nls(capitalization = Nls.Capitalization.Title) title: String,
                          @NlsContexts.Label private val description: String,
                          roots: Collection<GitRepository>,
                          defaultRoot: GitRepository?)
  : DialogWrapper(project, true) {

  private val rootComboBox: ComboBox<GitRepository> = ComboBox()

  init {
    roots.forEach { rootComboBox.addItem(it) }
    rootComboBox.selectedItem = if (roots.contains(defaultRoot)) defaultRoot else roots.first()
    rootComboBox.renderer = SimpleListCellRenderer.create(
      GitBundle.message("rebase.dialog.root.invalid.label.text"),
      GitRepository::getPresentableUrl
    )

    setTitle(title)
    setOKButtonText(title)
    init()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row {
        label(description)
      }
      row(GitBundle.message("common.git.root")) {
        rootComboBox()
      }
    }
  }

  override fun getPreferredFocusedComponent(): JComponent = rootComboBox

  fun selectRoot(): GitRepository? {
    return if (showAndGet()) rootComboBox.selectedItem as GitRepository? else null
  }
}