// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.prompt.AgentPromptBundle
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptExistingThreadsSnapshot
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.JBList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.DefaultListModel

@TestApplication
class AgentPromptExistingTaskControllerTest {
  @Test
  fun applySnapshotSortsEntriesDescendingAndCapsThemAtTwoHundred() {
    runInEdtAndWait {
      val fixture = controllerFixture()

      fixture.controller.applySnapshot(
        AgentPromptExistingThreadsSnapshot(
          threads = (0..204).map { index ->
            thread(id = "thread-$index", updatedAt = index.toLong(), title = "Thread $index")
          },
          isLoading = false,
          hasLoaded = true,
          hasError = false,
        )
      )

      assertThat(fixture.controller.entries).hasSize(200)
      assertThat(fixture.controller.entries.first().id).isEqualTo("thread-204")
      assertThat(fixture.controller.entries.last().id).isEqualTo("thread-5")
    }
  }

  @Test
  fun applySnapshotRetainsSelectionWhenSelectedThreadStillExists() {
    runInEdtAndWait {
      val fixture = controllerFixture()
      fixture.controller.selectedExistingTaskId = "thread-2"

      fixture.controller.applySnapshot(
        AgentPromptExistingThreadsSnapshot(
          threads = listOf(
            thread(id = "thread-1", updatedAt = 100),
            thread(id = "thread-2", updatedAt = 200),
          ),
          isLoading = false,
          hasLoaded = true,
          hasError = false,
        )
      )

      assertThat(fixture.controller.selectedExistingTaskId).isEqualTo("thread-2")
      assertThat(fixture.controller.selectedEntry()?.id).isEqualTo("thread-2")
    }
  }

  @Test
  fun applySnapshotClearsSelectionWhenSelectedThreadDisappears() {
    runInEdtAndWait {
      val fixture = controllerFixture()
      fixture.controller.selectedExistingTaskId = "missing"

      fixture.controller.applySnapshot(
        AgentPromptExistingThreadsSnapshot(
          threads = listOf(thread(id = "thread-1", updatedAt = 100)),
          isLoading = false,
          hasLoaded = true,
          hasError = false,
        )
      )

      assertThat(fixture.controller.selectedExistingTaskId).isNull()
      assertThat(fixture.controller.selectedEntry()).isNull()
    }
  }

  @Test
  fun applySnapshotShowsLoadingMessageWhileThreadsAreStillLoading() {
    runInEdtAndWait {
      val fixture = controllerFixture()

      fixture.controller.applySnapshot(
        AgentPromptExistingThreadsSnapshot(
          threads = emptyList(),
          isLoading = true,
          hasLoaded = false,
          hasError = false,
        )
      )

      assertThat(fixture.list.emptyText.text).isEqualTo(AgentPromptBundle.message("popup.existing.loading"))
    }
  }

  @Test
  fun applySnapshotShowsEmptyMessageWhenLoadedWithoutThreads() {
    runInEdtAndWait {
      val fixture = controllerFixture()

      fixture.controller.applySnapshot(
        AgentPromptExistingThreadsSnapshot(
          threads = emptyList(),
          isLoading = false,
          hasLoaded = true,
          hasError = false,
        )
      )

      assertThat(fixture.list.emptyText.text).isEqualTo(AgentPromptBundle.message("popup.existing.empty"))
    }
  }

  @Test
  fun applySnapshotShowsErrorMessageAndClearsSelectionOnError() {
    runInEdtAndWait {
      val fixture = controllerFixture()
      fixture.controller.selectedExistingTaskId = "thread-1"

      fixture.controller.applySnapshot(
        AgentPromptExistingThreadsSnapshot(
          threads = listOf(thread(id = "thread-1", updatedAt = 100)),
          isLoading = false,
          hasLoaded = true,
          hasError = true,
        )
      )

      assertThat(fixture.controller.selectedExistingTaskId).isNull()
      assertThat(fixture.list.emptyText.text).isEqualTo(AgentPromptBundle.message("popup.existing.error"))
    }
  }

  private fun controllerFixture(): ControllerFixture {
    val listModel = DefaultListModel<ThreadEntry>()
    val list = JBList(listModel)
    @Suppress("RAW_SCOPE_CREATION")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val controller = AgentPromptExistingTaskController(
      existingTaskListModel = listModel,
      existingTaskList = list,
      popupScope = scope,
      sessionsMessageResolver = AgentPromptSessionsMessageResolver(AgentPromptExistingTaskControllerTest::class.java.classLoader),
      onStateChanged = {},
    )
    return ControllerFixture(controller, list)
  }

  private fun thread(
    id: String,
    updatedAt: Long,
    title: String = id,
    activity: AgentThreadActivity = AgentThreadActivity.READY,
  ): AgentSessionThread {
    return AgentSessionThread(
      id = id,
      title = title,
      updatedAt = updatedAt,
      archived = false,
      activity = activity,
      provider = AgentSessionProvider.CODEX,
    )
  }

  private data class ControllerFixture(
    val controller: AgentPromptExistingTaskController,
    val list: JBList<ThreadEntry>,
  )
}
