// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.platform.projectView.ProjectViewPanesImpl
import com.intellij.platform.projectView.pane.projectViewPaneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class ProjectViewPanesTest {
  private val paneA = projectViewPaneId("PaneA")
  private val paneB = projectViewPaneId("PaneB")

  @Test
  fun `the current pane must be one of the panes`() {
    assertThrows<IllegalArgumentException> { ProjectViewPanesImpl(listOf(paneA), paneB) }
  }

  @Test
  fun `panes with equal IDs and an equal current pane are equal`() {
    assertEquals(ProjectViewPanesImpl(listOf(paneA, paneB), paneA), ProjectViewPanesImpl(listOf(paneA, paneB), paneA))
  }
}
