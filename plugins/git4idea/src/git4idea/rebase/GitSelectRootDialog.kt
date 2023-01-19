// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.dsl.builder.bindItemNullable
import com.intellij.ui.dsl.builder.panel
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

class GitSelectRootDialog(project: Project,
                          @Nls(capitalization = Nls.Capitalization.Title) title: String,
                          @NlsContexts.Label private val description: String,
                          val roots: Collection<GitRepository>,
                          defaultRoot: GitRepository?)
  : DialogWrapper(project, true) {

  private var repository: GitRepository? = if (roots.contains(defaultRoot)) defaultRoot else roots.first()

  init {
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
        comboBox(roots,
                 SimpleListCellRenderer.create(GitBundle.message("rebase.dialog.root.invalid.label.text"),
                                               GitRepository::getPresentableUrl))
          .bindItemNullable(::repository)
          .focused()
          .component
      }
    }
  }

  fun selectRoot(): GitRepository? {
    return if (showAndGet()) repository else null
  }
}