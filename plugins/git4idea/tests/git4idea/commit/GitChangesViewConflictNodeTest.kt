// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit

import com.intellij.openapi.vcs.changes.ChangesViewUtil
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserChangeListNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserConflictsNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport
import git4idea.test.GitScenarios.conflict
import git4idea.test.GitSingleRepoTest

internal class GitChangesViewConflictNodeTest : GitSingleRepoTest() {
  fun `test merge conflicts node is present if there are conflicts`() {
    conflict(repo, "feature")
    git("checkout feature")
    git("rebase master", true)
    updateChangeListManager()

    val groupingSupport = ChangesGroupingSupport(project = project, source = this, showConflictsNode = true).grouping
    val model = ChangesViewUtil.createTreeModel(project, groupingSupport, changeListManager.changeLists, emptyList(), emptyList()) { true }

    val root = model.root as ChangesBrowserNode<*>
    val rootChildren = root.iterateNodeChildren().toList()
    assertSize(1, rootChildren)
    val changeListsNode = rootChildren.single() as ChangesBrowserChangeListNode

    val changeListChildren = changeListsNode.iterateNodeChildren().toList()
    assertSize(1, changeListChildren)
    val conflictsNode = changeListChildren.single() as ChangesBrowserConflictsNode

    val conflictChanges = conflictsNode.allChangesUnder
    assertTrue("Conflicts node should contain conflicting changes", conflictChanges.isNotEmpty())
  }
}