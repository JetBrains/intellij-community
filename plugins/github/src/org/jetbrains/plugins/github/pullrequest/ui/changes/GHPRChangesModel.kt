// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.changes

import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import git4idea.GitCommit
import javax.swing.tree.DefaultTreeModel

/**
 * Either commits or changes should be not-null, but not both
 */
interface GHPRChangesModel {
  var commits: List<GitCommit>?
  var changes: List<Change>?

  fun buildChangesTree(grouping: ChangesGroupingPolicyFactory): DefaultTreeModel

  fun addStateChangesListener(listener: () -> Unit)
}