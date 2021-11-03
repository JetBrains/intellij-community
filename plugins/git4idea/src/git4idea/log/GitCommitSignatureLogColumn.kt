// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.vcs.log.ui.table.GraphTableModel
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.ui.table.column.VcsLogExternalStatusColumn
import com.intellij.vcs.log.ui.table.column.util.VcsLogExternalStatusColumnService
import git4idea.commit.signature.GitCommitSignature
import git4idea.i18n.GitBundle.message

internal class GitCommitSignatureLogColumn : VcsLogExternalStatusColumn<GitCommitSignature>() {
  override val id: String get() = GitCommitSignatureStatusProvider.ID
  override val localizedName: String get() = message("column.name.commit.signature")

  override fun getExternalStatusColumnService() =
    service<GitCommitSignatureColumnService>()

  override fun isEnabledByDefault(): Boolean = false

  override fun getStubValue(model: GraphTableModel): GitCommitSignature = GitCommitSignature.NoSignature

  override fun doCreateTableCellRenderer(table: VcsLogGraphTable) =
    GitCommitSignatureLogCellRenderer()
}

@Service
internal class GitCommitSignatureColumnService : VcsLogExternalStatusColumnService<GitCommitSignature>() {
  override fun getDataLoader(project: Project) = GitCommitSignatureStatusProvider().createLoader(project)
}