// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.common.CodexThreadPage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexSessionsPagingLogicTest {
  @Test
  fun seedInitialVisibleThreadsFetchesUntilMinimumIsReached() = runBlocking {
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
        assertEquals("cursor-1", cursor)
        CodexThreadPage(
          threads = listOf(
            CodexThread(id = "thread-3", title = "Thread 3", updatedAt = 3_000L, archived = false),
          ),
          nextCursor = null,
        )
      },
    )

    assertEquals(1, pageRequests)
    assertEquals(listOf("thread-3", "thread-2", "thread-1"), result.threads.map { it.id })
    assertEquals(null, result.nextCursor)
  }

  @Test
  fun seedInitialVisibleThreadsStopsWhenCursorRepeatsWithoutProgress() = runBlocking {
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

    assertEquals(1, pageRequests)
    assertEquals(listOf("thread-1"), result.threads.map { it.id })
    assertEquals("cursor-1", result.nextCursor)
  }

  @Test
  fun seedInitialVisibleThreadsStopsOnCursorLoop() = runBlocking {
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

    assertEquals(listOf("cursor-1", "cursor-2"), requests)
    assertTrue(result.threads.size == 1)
    assertEquals("cursor-1", result.nextCursor)
  }
}

