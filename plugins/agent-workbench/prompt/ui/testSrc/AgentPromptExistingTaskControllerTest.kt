// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.prompt.core.AgentPromptExistingThreadsSnapshot
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.JBList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.assertNotNull
import javax.swing.DefaultListModel

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
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
          threads = listOf(
            thread(id = "thread-1", updatedAt = 100),
            thread(id = "thread-2", updatedAt = 200),
          ),
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

  @Test
  fun applySnapshotPreselectsLoneThreadWhenNoExistingSelection() {
    runInEdtAndWait {
      val fixture = controllerFixture()

      fixture.controller.applySnapshot(
        AgentPromptExistingThreadsSnapshot(
          threads = listOf(thread(id = "thread-1", updatedAt = 100)),
          isLoading = false,
          hasLoaded = true,
          hasError = false,
        )
      )

      assertThat(fixture.controller.selectedExistingTaskId).isEqualTo("thread-1")
      assertThat(fixture.list.selectedIndex).isEqualTo(0)
    }
  }

  @Test
  fun applySnapshotDoesNotPreselectAnythingForMultiEntryWithoutHint() {
    runInEdtAndWait {
      val fixture = controllerFixture()

      fixture.controller.applySnapshot(
        AgentPromptExistingThreadsSnapshot(
          threads = listOf(
            thread(id = "thread-1", updatedAt = 100),
            thread(id = "thread-2", updatedAt = 200),
            thread(id = "thread-3", updatedAt = 300),
          ),
          isLoading = false,
          hasLoaded = true,
          hasError = false,
        )
      )

      assertThat(fixture.controller.selectedExistingTaskId).isNull()
      assertThat(fixture.list.selectedIndex).isEqualTo(-1)
    }
  }

  @Test
  fun setPreselectionAppliesIdWhenInLoadedList() {
    runInEdtAndWait {
      val fixture = controllerFixture()
      fixture.controller.applySnapshot(
        AgentPromptExistingThreadsSnapshot(
          threads = listOf(
            thread(id = "thread-1", updatedAt = 100),
            thread(id = "thread-2", updatedAt = 200),
            thread(id = "thread-3", updatedAt = 300),
          ),
          isLoading = false,
          hasLoaded = true,
          hasError = false,
        )
      )

      fixture.controller.setPreselection("thread-2")

      assertThat(fixture.controller.selectedExistingTaskId).isEqualTo("thread-2")
      assertThat(fixture.list.selectedValue?.id).isEqualTo("thread-2")
    }
  }

  @Test
  fun setPreselectionIgnoresIdNotInLoadedList() {
    runInEdtAndWait {
      val fixture = controllerFixture()
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

      fixture.controller.setPreselection("thread-missing")

      assertThat(fixture.controller.selectedExistingTaskId).isNull()
    }
  }

  @Test
  fun setPreselectionDoesNotOverrideExistingSelection() {
    runInEdtAndWait {
      val fixture = controllerFixture()
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
      val thread1Entry = fixture.controller.entries.firstOrNull { it.id == "thread-1" }
      assertNotNull(thread1Entry, "Expected thread-1 entry to be loaded before selecting it")
      fixture.controller.onUserSelected(thread1Entry)

      fixture.controller.setPreselection("thread-2")

      assertThat(fixture.controller.selectedExistingTaskId).isEqualTo("thread-1")
    }
  }

  @Test
  fun setPreselectionAppliedAfterSnapshotArrivesLate() {
    runInEdtAndWait {
      val fixture = controllerFixture()

      fixture.controller.setPreselection("thread-2")
      assertThat(fixture.controller.selectedExistingTaskId).isNull()

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

      fixture.controller.setPreselection("thread-2")

      assertThat(fixture.controller.selectedExistingTaskId).isEqualTo("thread-2")
      assertThat(fixture.list.selectedValue?.id).isEqualTo("thread-2")
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
    @JvmField val controller: AgentPromptExistingTaskController,
    @JvmField val list: JBList<ThreadEntry>,
  )
}
