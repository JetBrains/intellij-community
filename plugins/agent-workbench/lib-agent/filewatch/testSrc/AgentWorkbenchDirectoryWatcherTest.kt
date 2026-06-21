// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.filewatch

import com.intellij.agent.workbench.filewatch.impl.DirectoryChangeEvent
import com.intellij.agent.workbench.filewatch.impl.DirectoryChangeListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentWorkbenchDirectoryWatcherTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun pathlessOverflowIsDelivered() = runBlocking(Dispatchers.Default) {
    val watchLoop = FakeWatchLoop()
    val events = LinkedBlockingQueue<AgentWorkbenchWatchEvent>()
    val failures = LinkedBlockingQueue<Throwable>()

    AgentWorkbenchDirectoryWatcher(
      roots = listOf(tempDir),
      scope = this,
      onWatchEvent = events::add,
      onFailure = failures::add,
      watchLoopFactory = { _, listener ->
        watchLoop.listener = listener
        watchLoop
      },
    ).use { watcher ->
      watchLoop.offer(DirectoryChangeEvent(DirectoryChangeEvent.EventType.OVERFLOW, false, null, 1, tempDir.toAbsolutePath()))

      val event = withTimeout(5.seconds) {
        events.take()
      }

      assertThat(event.eventType).isEqualTo(AgentWorkbenchWatchEventType.OVERFLOW)
      assertThat(event.path).isNull()
      assertThat(event.rootPath).isEqualTo(tempDir.toAbsolutePath())
      assertThat(event.count).isEqualTo(1)
      assertThat(failures).isEmpty()
      watcher.close()
    }
  }

  @Test
  fun rootPathIsPreservedForDeliveredEvents() {
    runBlocking(Dispatchers.Default) {
      val watchLoop = FakeWatchLoop()
      val events = LinkedBlockingQueue<AgentWorkbenchWatchEvent>()
      val root = tempDir.toAbsolutePath()
      val changedPath = root.resolve("session.jsonl")

      AgentWorkbenchDirectoryWatcher(
        roots = listOf(tempDir),
        scope = this,
        onWatchEvent = events::add,
        watchLoopFactory = { _, listener ->
          watchLoop.listener = listener
          watchLoop
        },
      ).use {
        watchLoop.offer(DirectoryChangeEvent(DirectoryChangeEvent.EventType.CREATE, false, changedPath, 1, root))

        val event = withTimeout(5.seconds) {
          events.take()
        }

        assertThat(event.eventType).isEqualTo(AgentWorkbenchWatchEventType.CREATE)
        assertThat(event.path).isEqualTo(changedPath)
        assertThat(event.rootPath).isEqualTo(root)
      }
    }
  }

  @Test
  fun closeSuppressesLateEventsAndFailures() = runBlocking(Dispatchers.Default) {
    val watchLoop = FakeWatchLoop()
    val events = LinkedBlockingQueue<AgentWorkbenchWatchEvent>()
    val failures = LinkedBlockingQueue<Throwable>()
    val root = tempDir.toAbsolutePath()

    AgentWorkbenchDirectoryWatcher(
      roots = listOf(tempDir),
      scope = this,
      onWatchEvent = events::add,
      onFailure = failures::add,
      watchLoopFactory = { _, listener ->
        watchLoop.listener = listener
        watchLoop
      },
    ).use { watcher ->
      watcher.close()

      watchLoop.offer(DirectoryChangeEvent(DirectoryChangeEvent.EventType.CREATE, false, root.resolve("late.jsonl"), 1, root))
      watchLoop.fail(IllegalStateException("late failure"))
      delay(200.milliseconds)

      assertThat(events).isEmpty()
      assertThat(failures).isEmpty()
    }
  }

  @Test
  fun watcherWithNoValidRootsIsInactive() {
    runBlocking(Dispatchers.Default) {
      val watcher = AgentWorkbenchDirectoryWatcher(
        roots = listOf(tempDir.resolve("missing")),
        scope = this,
        onWatchEvent = {},
      )

      assertThat(watcher.isActive).isFalse()
    }
  }

  @Test
  fun closeMakesWatcherInactive() {
    runBlocking(Dispatchers.Default) {
      val watchLoop = FakeWatchLoop()
      val watcher = AgentWorkbenchDirectoryWatcher(
        roots = listOf(tempDir),
        scope = this,
        onWatchEvent = {},
        watchLoopFactory = { _, listener ->
          watchLoop.listener = listener
          watchLoop
        },
      )

      assertThat(watcher.isActive).isTrue()

      watcher.closeAndJoin()

      assertThat(watcher.isActive).isFalse()
    }
  }

  @Test
  fun completedWatchLoopMakesWatcherInactive() {
    runBlocking(Dispatchers.Default) {
      val watchLoop = CompletingWatchLoop()
      val watcher = AgentWorkbenchDirectoryWatcher(
        roots = listOf(tempDir),
        scope = this,
        onWatchEvent = {},
        watchLoopFactory = { _, _ -> watchLoop },
      )

      withTimeout(5.seconds) {
        watchLoop.completed.await()
      }

      assertThat(watcher.isActive).isFalse()
    }
  }

  @Test
  fun failedWatchLoopRestartsWithSameRoots() {
    runBlocking(Dispatchers.Default) {
      val events = LinkedBlockingQueue<AgentWorkbenchWatchEvent>()
      val failures = LinkedBlockingQueue<Throwable>()
      val restartedLoop = CompletableDeferred<FakeWatchLoop>()
      val root = tempDir.toAbsolutePath()
      var createCount = 0

      AgentWorkbenchDirectoryWatcher(
        roots = listOf(tempDir),
        scope = this,
        onWatchEvent = events::add,
        onFailure = failures::add,
        watchLoopFactory = { _, listener ->
          createCount += 1
          if (createCount == 1) {
            FailingWatchLoop(IllegalStateException("watch loop failed"))
          }
          else {
            FakeWatchLoop().also { loop ->
              loop.listener = listener
              restartedLoop.complete(loop)
            }
          }
        },
      ).use { watcher ->
        val failure = withTimeout(5.seconds) { failures.take() }
        val loop = withTimeout(5.seconds) { restartedLoop.await() }

        loop.offer(DirectoryChangeEvent(DirectoryChangeEvent.EventType.CREATE, false, root.resolve("session.jsonl"), 1, root))
        val event = withTimeout(5.seconds) { events.take() }

        assertThat(failure).hasMessage("watch loop failed")
        assertThat(event.path).isEqualTo(root.resolve("session.jsonl"))
        assertThat(watcher.isActive).isTrue()
      }
    }
  }
}

private class CompletingWatchLoop : AgentWorkbenchWatchLoop {
  val completed = CompletableDeferred<Unit>()

  override suspend fun watch() {
    completed.complete(Unit)
  }

  override fun close() {
  }
}

private class FailingWatchLoop(private val error: Throwable) : AgentWorkbenchWatchLoop {
  override suspend fun watch() {
    throw error
  }

  override fun close() {
  }
}

private class FakeWatchLoop : AgentWorkbenchWatchLoop {
  private val events = LinkedBlockingQueue<Any>()
  lateinit var listener: DirectoryChangeListener

  @Volatile
  private var closed = false

  private object Stop

  fun offer(event: DirectoryChangeEvent) {
    events.add(event)
  }

  fun fail(e: Exception) {
    events.add(e)
  }

  override suspend fun watch() {
    while (!closed) {
      when (val event = events.take()) {
        Stop -> return
        is DirectoryChangeEvent -> listener.onEvent(event)
        is Exception -> listener.onException(event)
      }
    }
  }

  override fun close() {
    closed = true
    events.offer(Stop)
  }
}
