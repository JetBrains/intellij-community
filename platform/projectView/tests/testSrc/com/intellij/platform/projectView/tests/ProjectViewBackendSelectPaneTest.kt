// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.tests

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.projectView.ProjectViewPanesImpl
import com.intellij.platform.projectView.frontend.window.ProjectViewToolWindowServiceImpl
import com.intellij.platform.projectView.frontend.window.syncPaneSelectionWithBackend
import com.intellij.platform.projectView.impl.BackendProjectView
import com.intellij.platform.projectView.pane.projectViewPaneId
import com.intellij.platform.projectView.rpc.ProjectViewRpc
import com.intellij.platform.projectView.rpc.SelectPaneRequestDTO
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests the [BackendProjectView] of remote development.
 *
 * The test acts as the frontend through the in-process [ProjectViewRpc], or runs the real frontend sync.
 */
@TestApplication
@RegistryKey(key = "project.view.toolwindow.split.monolith", value = "true")
internal class ProjectViewBackendSelectPaneTest {
  private val projectFixture = projectFixture()
  private val project: Project get() = projectFixture.get()
  private val backend: BackendProjectView get() = project.service<BackendProjectView>()

  private suspend fun rpc(): ProjectViewRpc = ProjectViewRpc.awaitConnectionAndGetInstance()

  private suspend fun requests(): Flow<SelectPaneRequestDTO?> = rpc().getSelectPaneRequestFlow(project.projectId())

  @Test
  fun `the backend keeps the panes of the frontend after the channel closes`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    val panes = ProjectViewPanesImpl(listOf(PANE_A, PANE_B), PANE_B)
    val panesChannel = rpc().getPanesChannel(project.projectId())
    panesChannel.send(panes)
    backend.panes.first { it == panes }

    panesChannel.close()
    rpc().getPanesChannel(project.projectId()) // returns after the loop of the closed channel finishes

    assertEquals(panes, backend.panes.value)
  }

  @Test
  fun `a new panes channel replaces the previous one`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    val oldPanes = ProjectViewPanesImpl(listOf(PANE_A), PANE_A)
    val newPanes = ProjectViewPanesImpl(listOf(PANE_A, PANE_B), PANE_B)
    val oldChannel = rpc().getPanesChannel(project.projectId())
    oldChannel.send(oldPanes)
    backend.panes.first { it == oldPanes }

    val newChannel = rpc().getPanesChannel(project.projectId())
    newChannel.send(newPanes)
    backend.panes.first { it == newPanes }

    oldChannel.send(oldPanes)
    val stalePanes = withTimeoutOrNull(STALE_PANES_WAIT) { backend.panes.first { it == oldPanes } }
    assertNull(stalePanes, "The backend must ignore the panes from the old channel")
    assertEquals(newPanes, backend.panes.value)
  }

  @Test
  fun `the result of the frontend completes the request`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    val result = async { backend.selectPane(PANE_A) }
    val request = requests().filterNotNull().first()
    assertEquals(PANE_A, request.paneId)

    rpc().completeSelectPaneRequest(project.projectId(), request.requestId, true)

    assertTrue(result.await())
    assertNull(requests().first())
  }

  @Test
  fun `a later request of the backend supersedes the pending one`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    val earlier = async { backend.selectPane(PANE_A) }
    val earlierRequest = requests().filterNotNull().first()
    val later = async { backend.selectPane(PANE_B) }

    assertFalse(earlier.await())
    val laterRequest = requests().filterNotNull().first { it.requestId != earlierRequest.requestId }
    assertEquals(PANE_B, laterRequest.paneId)

    rpc().completeSelectPaneRequest(project.projectId(), earlierRequest.requestId, true) // ignored, because it isn't pending
    rpc().completeSelectPaneRequest(project.projectId(), laterRequest.requestId, true)
    assertTrue(later.await())
  }

  @Test
  fun `a canceled call withdraws its request`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    val call = launch { backend.selectPane(PANE_A) }
    requests().filterNotNull().first()

    call.cancelAndJoin()

    assertNull(requests().first())
  }

  @Test
  fun `the backend selects a pane through the frontend sync`() = timeoutRunBlocking(PANE_TEST_TIMEOUT) {
    withManagedProjectViewToolWindow(project) {
      val frontend = ProjectViewToolWindowServiceImpl.getInstance(project)
      val sync = launch { syncPaneSelectionWithBackend(project, frontend) }
      val startupPaneId = awaitStartupPaneSelection(project)
      backend.panes.first { it == frontend.panes.value }
      val targetPaneId = frontend.panes.value.paneIds.first { it != startupPaneId }

      assertTrue(backend.selectPane(targetPaneId))
      assertEquals(targetPaneId, frontend.panes.value.currentPaneId)
      backend.panes.first { it.currentPaneId == targetPaneId }

      assertFalse(backend.selectPane(projectViewPaneId("NoSuchPane")))
      sync.cancelAndJoin()
    }
  }
}

private val PANE_A = projectViewPaneId("PaneA")
private val PANE_B = projectViewPaneId("PaneB")

/**
 * How long a test waits to see that stale panes never come.
 */
private val STALE_PANES_WAIT: Duration = 200.milliseconds
