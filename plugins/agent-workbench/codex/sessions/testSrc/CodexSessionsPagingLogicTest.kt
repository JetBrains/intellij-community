// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.common.CodexThreadPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class CodexSessionsPagingLogicTest {
  @Test
  fun seedInitialVisibleThreadsFetchesUntilMinimumIsReached(): Unit = runBlocking(Dispatchers.Default) {
    var pageRequests = 0
    val result = seedInitialVisibleThreads(
      initialPage = CodexThreadPage(
        threads = listOf(
          CodexThread(id = "thread-1", title = "Thread 1", updatedAt = 1_000L, archived = false),
          CodexThread(id = "thread-2", title = "Thread 2", updatedAt = 2_000L, archived = false),
        ),
        nextCursor = "cursor-1",
      ),
      minimumVisibleThreads = 3,
      loadNextPage = { cursor ->
        pageRequests += 1
        assertThat(cursor).isEqualTo("cursor-1")
        CodexThreadPage(
          threads = listOf(
            CodexThread(id = "thread-3", title = "Thread 3", updatedAt = 3_000L, archived = false),
          ),
          nextCursor = null,
        )
      },
    )

    assertThat(pageRequests).isEqualTo(1)
    assertThat(result.threads.map { it.id }).isEqualTo(listOf("thread-3", "thread-2", "thread-1"))
    assertThat(result.nextCursor).isNull()
  }

  @Test
  fun seedInitialVisibleThreadsStopsWhenCursorRepeatsWithoutProgress(): Unit = runBlocking(Dispatchers.Default) {
    var pageRequests = 0
    val result = seedInitialVisibleThreads(
      initialPage = CodexThreadPage(
        threads = listOf(
          CodexThread(id = "thread-1", title = "Thread 1", updatedAt = 2_000L, archived = false),
        ),
        nextCursor = "cursor-1",
      ),
      minimumVisibleThreads = 3,
      loadNextPage = {
        pageRequests += 1
        CodexThreadPage(
          threads = listOf(
            CodexThread(id = "thread-1", title = "Thread 1", updatedAt = 1_000L, archived = false),
          ),
          nextCursor = "cursor-1",
        )
      },
    )

    assertThat(pageRequests).isEqualTo(1)
    assertThat(result.threads.map { it.id }).isEqualTo(listOf("thread-1"))
    assertThat(result.nextCursor).isEqualTo("cursor-1")
  }

  @Test
  fun seedInitialVisibleThreadsStopsOnCursorLoop(): Unit = runBlocking(Dispatchers.Default) {
    val requests = mutableListOf<String>()
    val result = seedInitialVisibleThreads(
      initialPage = CodexThreadPage(
        threads = listOf(
          CodexThread(id = "thread-1", title = "Thread 1", updatedAt = 1_000L, archived = false),
        ),
        nextCursor = "cursor-1",
      ),
      minimumVisibleThreads = 3,
      loadNextPage = { cursor ->
        requests += cursor
        if (cursor == "cursor-1") {
          CodexThreadPage(
            threads = listOf(
              CodexThread(id = "thread-1", title = "Thread 1", updatedAt = 1_000L, archived = false),
            ),
            nextCursor = "cursor-2",
          )
        }
        else {
          CodexThreadPage(
            threads = listOf(
              CodexThread(id = "thread-1", title = "Thread 1", updatedAt = 1_000L, archived = false),
            ),
            nextCursor = "cursor-1",
          )
        }
      },
    )

    assertThat(requests).isEqualTo(listOf("cursor-1", "cursor-2"))
    assertThat(result.threads).hasSize(1)
    assertThat(result.nextCursor).isEqualTo("cursor-1")
  }
}
