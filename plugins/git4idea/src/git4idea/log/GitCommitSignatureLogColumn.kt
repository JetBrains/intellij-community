// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.vcs.log.ui.table.GraphTableModel
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.ui.table.column.VcsLogCustomColumn
import git4idea.i18n.GitBundle.message
import javax.swing.table.TableCellRenderer

internal class GitCommitSignatureLogColumn : VcsLogCustomColumn<Any> {
  override val id: String get() = "Git.CommitSignature"
  override val localizedName: String get() = message("column.name.commit.signature")

  override val isDynamic: Boolean get() = true
  override fun isEnabledByDefault(): Boolean = false

  override val isResizable: Boolean get() = false

  override fun getValue(model: GraphTableModel, row: Int): Any = ""
  override fun getStubValue(model: GraphTableModel): Any = ""

  override fun createTableCellRenderer(table: VcsLogGraphTable): TableCellRenderer =
    GitCommitSignatureLogCellRenderer()
}