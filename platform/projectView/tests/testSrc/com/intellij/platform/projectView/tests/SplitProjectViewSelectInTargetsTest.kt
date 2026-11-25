// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.platform.projectView.frontend.actions.SplitProjectViewSelectInGroupTarget
import com.intellij.platform.projectView.frontend.actions.createSelectInTargets
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneKind
import com.intellij.platform.projectView.pane.SelectInTargetDescriptor
import com.intellij.platform.projectView.pane.SelectInTargetGroupImpl
import com.intellij.platform.projectView.pane.projectViewPaneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Tests how [createSelectInTargets] puts the Select In targets of the panes into groups. */
internal class SplitProjectViewSelectInTargetsTest {
  private val group = SelectInTargetGroupImpl(id = "group", presentableName = "Group", weight = 2f)

  @Test
  fun `the targets of one group become one target in the place of the first one`() {
    val targets = createSelectInTargets(listOf(
      pane("a", target("A", 1f)),
      pane("b", target("B", 2f, group)),
      pane("c", target("C1", 4f), target("C2", 3f, group)),
    ))

    assertEquals(listOf("A", "Group", "C1"), targets.map { it.toString() })
    assertEquals(listOf(1f, 2f, 4f), targets.map { it.weight })
    assertEquals(listOf(paneId("b"), paneId("c")), (targets[1] as SplitProjectViewSelectInGroupTarget).paneIds)
  }

  @Test
  fun `the panes of a group are in the order of the weights of their targets`() {
    val targets = createSelectInTargets(listOf(
      pane("a", target("A", 3f, group)),
      pane("b", target("B1", 1f, group), target("B2", 2f, group)),
    ))

    assertEquals(listOf(paneId("b"), paneId("a")), targets.single().paneIds)
  }

  @Test
  fun `the first target of a group decides the name and the weight of the group`() {
    val otherGroup = SelectInTargetGroupImpl(id = group.id, presentableName = "Other Group", weight = 5f)

    val targets = createSelectInTargets(listOf(
      pane("a", target("A", 1f, group)),
      pane("b", target("B", 1f, otherGroup)),
    ))

    assertEquals("Group", targets.single().toString())
    assertEquals(group.weight, targets.single().weight)
  }

  private fun paneId(id: String) = projectViewPaneId(id)

  private fun target(name: String, weight: Float, group: SelectInTargetGroupImpl? = null) = SelectInTargetDescriptor(name, weight, group)

  private fun pane(id: String, vararg targets: SelectInTargetDescriptor) = ProjectViewPaneDescriptorImpl(
    id = paneId(id),
    kind = ProjectViewPaneKind.BACKEND,
    presentableName = id,
    order = 0,
    isDefault = false,
    icon = null,
    selectInTargetDescriptors = targets.toList(),
  )
}
