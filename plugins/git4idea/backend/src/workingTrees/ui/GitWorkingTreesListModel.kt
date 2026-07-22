// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import javax.swing.DefaultListModel

internal class GitWorkingTreesListModel : DefaultListModel<GitWorktreeRow>() {
  fun setRows(rows: List<GitWorktreeRow>) {
    clear()
    rows.forEach { addElement(it) }
  }
}
