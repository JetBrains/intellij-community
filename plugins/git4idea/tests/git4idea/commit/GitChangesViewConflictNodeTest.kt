// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit

import com.intellij.openapi.vcs.changes.ChangesViewUtil
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserChangeListNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserConflictsNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.updateChangeListManager
import git4idea.test.GitScenarios.conflict
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class GitChangesViewConflictNodeTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @Test
  fun `test merge conflicts node is present if there are conflicts`(): Unit = with(context) {
    conflict(repo, "feature")
    git("checkout feature")
    git("rebase master", true)
    updateChangeListManager()

    val groupingSupport = ChangesGroupingSupport(project = project, source = this, showConflictsNode = true).grouping
    val model = ChangesViewUtil.createTreeModel(project, groupingSupport, changeListManager.changeLists, emptyList(), emptyList()) { true }

    val root = model.root as ChangesBrowserNode<*>
    val rootChildren = root.iterateNodeChildren().toList()
    assertThat(rootChildren).hasSize(1)
    val changeListsNode = rootChildren.single() as ChangesBrowserChangeListNode

    val changeListChildren = changeListsNode.iterateNodeChildren().toList()
    assertThat(changeListChildren).hasSize(1)
    val conflictsNode = changeListChildren.single() as ChangesBrowserConflictsNode

    val conflictChanges = conflictsNode.allChangesUnder
    assertThat(conflictChanges).isNotEmpty()
  }
}
