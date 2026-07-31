// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import javax.swing.DefaultListModel

internal class GitWorkingTreesListModel : DefaultListModel<GitWorkingTreesListEntry>() {
  /** Whether entries are grouped under repository headers (peer multi-root case). */
  var grouped: Boolean = false
    private set

  fun setEntries(entries: List<GitWorkingTreesListEntry>) {
    grouped = entries.any { it is GitRepositoryHeader }
    clear()
    entries.forEach { addElement(it) }
  }
}
