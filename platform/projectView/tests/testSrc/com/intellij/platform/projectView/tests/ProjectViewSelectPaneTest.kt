// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.ProjectView
import com.intellij.platform.projectView.frontend.pane.FrontendProjectViewPaneAggregator
import com.intellij.platform.projectView.frontend.pane.id
import com.intellij.platform.projectView.frontend.window.ProjectViewToolWindowServiceImpl
import com.intellij.platform.projectView.impl.project.ProjectPaneModel
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.projectViewPaneId
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests [ProjectView.selectPane] in the monolith, where the frontend tool window service decides each request.
 *
 * The real tool window service manages a mock tool window, and the panes come from the real backend through the in-process RPC.
 */
@TestApplication
@RegistryKey(key = "project.view.toolwindow.split.monolith", value = "true")
internal class ProjectViewSelectPaneTest {
  private val projectFixture = projectFixture()
  private val project: Project get() = projectFixture.get()
  private val projectView: ProjectView get() = ProjectView.getInstance(project)

  @Test
  fun `selectPane makes an existing pane current`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    withManagedProjectViewToolWindow(project) {
      val startupPaneId = awaitStartupPaneSelection(project)
      val otherPaneId = projectView.panes.value.paneIds.first { it != startupPaneId }

      assertTrue(projectView.selectPane(otherPaneId))
      assertEquals(otherPaneId, projectView.panes.value.currentPaneId)

      assertTrue(projectView.selectPane(otherPaneId), "The current pane must stay current")
      assertEquals(otherPaneId, projectView.panes.value.currentPaneId)
    }
  }

  @Test
  fun `selectPane returns false for a pane that the tool window doesn't have`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    withManagedProjectViewToolWindow(project) {
      val startupPaneId = awaitStartupPaneSelection(project)

      assertFalse(projectView.selectPane(projectViewPaneId("NoSuchPane")))
      assertEquals(startupPaneId, projectView.panes.value.currentPaneId)
    }
  }

  @Test
  fun `selectPane waits for the initial panes and wins over the startup selection`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    val targetPaneId = FrontendProjectViewPaneAggregator.getInstance(project).getPaneDescriptorsFlow()
      .first { descriptors -> descriptors.any { !it.isDefault } }
      .first { !it.isDefault }
      .id
    val result = async { projectView.selectPane(targetPaneId) }

    withManagedProjectViewToolWindow(project) {
      assertTrue(result.await())
      assertEquals(targetPaneId, projectView.panes.value.currentPaneId)
    }
  }

  @Test
  fun `a later request supersedes an earlier one`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    withManagedProjectViewToolWindow(project) {
      val startupPaneId = awaitStartupPaneSelection(project)
      val otherPaneId = projectView.panes.value.paneIds.first { it != startupPaneId }

      val (earlier, later) = withContext(Dispatchers.EDT) {
        // The EDT is busy until this block ends, so neither request can select its pane before both requests come.
        val earlier = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { projectView.selectPane(otherPaneId) }
        val later = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { projectView.selectPane(startupPaneId) }
        earlier to later
      }

      assertFalse(earlier.await())
      assertTrue(later.await())
      assertEquals(startupPaneId, projectView.panes.value.currentPaneId)
    }
  }

  @Test
  fun `a selection in the tool window supersedes a request`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    withManagedProjectViewToolWindow(project) { toolWindow ->
      val startupPaneId = awaitStartupPaneSelection(project)
      val paneIds = projectView.panes.value.paneIds
      val requestedPaneId = paneIds.first { it != startupPaneId }
      val userPaneId = paneIds.firstOrNull { it != startupPaneId && it != requestedPaneId } ?: startupPaneId
      val userContent = checkNotNull(toolWindow.contentManager.getContent(
        ProjectViewToolWindowServiceImpl.getInstance(project).panesSnapshot.value.panes.first { it.id == userPaneId }.component
      ))

      val request = withContext(Dispatchers.EDT) {
        // The EDT is busy until this block ends, so the request can't select its pane before the user does.
        val request = async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { projectView.selectPane(requestedPaneId) }
        toolWindow.contentManager.setSelectedContent(userContent)
        request
      }

      assertFalse(request.await())
      assertEquals(userPaneId, projectView.panes.value.currentPaneId)
    }
  }
}

/**
 * Runs [block] while the Project View tool window service of [project] manages a mock tool window.
 */
internal suspend fun <T> withManagedProjectViewToolWindow(
  project: Project,
  block: suspend CoroutineScope.(ToolWindowHeadlessManagerImpl.MockToolWindow) -> T,
): T = coroutineScope {
  val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(project)
  val managementJob = launch(CoroutineName("Test PV tool window management")) {
    ProjectViewToolWindowServiceImpl.getInstance(project).manageToolWindow(toolWindow)
  }
  try {
    block(toolWindow)
  }
  finally {
    managementJob.cancelAndJoin()
  }
}

/**
 * Waits until the tool window has selected its startup pane, and returns the ID of that pane.
 * The project has no saved state, so the startup pane is the default pane.
 */
internal suspend fun awaitStartupPaneSelection(project: Project): ProjectViewPaneId {
  val descriptors = FrontendProjectViewPaneAggregator.getInstance(project).getPaneDescriptorsFlow().first { it.size > 1 }
  val startupPaneId = descriptors.firstOrNull { it.isDefault }?.id ?: ProjectPaneModel.ID
  ProjectView.getInstance(project).panes.first { it.currentPaneId == startupPaneId && it.paneIds.size > 1 }
  return startupPaneId
}
