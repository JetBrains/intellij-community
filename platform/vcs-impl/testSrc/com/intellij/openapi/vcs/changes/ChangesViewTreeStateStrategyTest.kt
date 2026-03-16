// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.ChangeListRemoteState
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserChangeListNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.testFramework.ProjectRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests for ChangesViewTreeStateStrategy default changelist expansion behavior (pure logic, no Swing tree).
 */
class ChangesViewTreeStateStrategyTest {
  @JvmField
  @Rule
  val projectRule = ProjectRule()

  @Test
  fun `expands default changelist when no other changelists expanded`() {
    val project = projectRule.project
    val (_, _, root) = buildModelWithTwoLists(project)

    val shouldExpand = ChangesViewTreeStateStrategy().shouldExpandDefaultChangeList(
      newRoot = root,
      oldFileCount = 0,
      isNodeExpanded = { _ -> false }, // no non-default expanded
    )

    assertTrue("Default changelist should be expanded when nothing else is expanded", shouldExpand)
  }

  @Test
  fun `does not expand default changelist when a non-default is expanded`() {
    val project = projectRule.project
    val (_, nonDefaultNode, root) = buildModelWithTwoLists(project)

    val shouldExpand = ChangesViewTreeStateStrategy().shouldExpandDefaultChangeList(
      newRoot = root,
      oldFileCount = 0,
      isNodeExpanded = { it === nonDefaultNode }, // non-default already expanded
    )

    assertFalse("Default changelist must NOT auto-expand when another changelist is expanded", shouldExpand)
  }

  @Test
  fun `doesn't expand default changelist if it wasn't empty, no matter the other expansions`() {
    val project = projectRule.project
    val (_, nonDefaultNode, root) = buildModelWithTwoLists(project)

    val shouldExpandWhenNothingElseExpanded = ChangesViewTreeStateStrategy().shouldExpandDefaultChangeList(
      newRoot = root,
      oldFileCount = 10,
      isNodeExpanded = { _ -> false }, // no non-default expanded
    )

    val shouldExpandWhenSomethingElseExpanded = ChangesViewTreeStateStrategy().shouldExpandDefaultChangeList(
      newRoot = root,
      oldFileCount = 10,
      isNodeExpanded = { it == nonDefaultNode }, // no non-default expanded
    )

    assertTrue("Default changelist should not be automatically expanded when it wasn't empty", !shouldExpandWhenSomethingElseExpanded && !shouldExpandWhenNothingElseExpanded)
  }

  private fun buildModelWithTwoLists(project: Project): Triple<ChangesBrowserChangeListNode, ChangesBrowserChangeListNode, ChangesBrowserNode<*>> {
    val root = ChangesBrowserNode.createRoot()

    val defaultList = LocalChangeListImpl.Builder(project, "Default").setDefault(true).build()
    val nonDefaultList = LocalChangeListImpl.Builder(project, "Work").setDefault(false).build()

    val defaultNode = ChangesBrowserChangeListNode(project, defaultList, ChangeListRemoteState())
    val nonDefaultNode = ChangesBrowserChangeListNode(project, nonDefaultList, ChangeListRemoteState())

    root.add(defaultNode)
    root.add(nonDefaultNode)

    return Triple(defaultNode, nonDefaultNode, root)
  }
}
