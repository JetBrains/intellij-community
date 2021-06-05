// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.icons.AllIcons
import com.intellij.vcs.log.ui.table.VcsLogGraphTable
import com.intellij.vcs.log.ui.table.VcsLogIconCellRenderer

internal class GitCommitSignatureLogCellRenderer : VcsLogIconCellRenderer() {
  override fun customize(table: VcsLogGraphTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int) {
    icon = AllIcons.RunConfigurations.ToolbarPassed
  }
}
