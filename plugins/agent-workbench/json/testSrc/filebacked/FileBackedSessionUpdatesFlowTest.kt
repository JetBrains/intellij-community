// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.json.filebacked

import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FileBackedSessionUpdatesFlowTest {
  @Test
  fun staysIdleWhenWatcherWasNotInitialized() = runBlocking(Dispatchers.Default) {
    val update = withTimeoutOrNull(1.seconds) {
      createFileBackedSessionChangeFlow(
        logger = logger<FileBackedSessionUpdatesFlowTest>(),
        watcherName = "test watcher",
      ) { _, _ -> null }.first()
    }

    assertThat(update).isNull()
  }

  @Test
  fun emitsInitialRefreshPingWhenConfigured() {
    runBlocking(Dispatchers.Default) {
      val update = withTimeout(5.seconds) {
        createFileBackedSessionChangeFlow(
          logger = logger<FileBackedSessionUpdatesFlowTest>(),
          watcherName = "test watcher",
          emitInitialRefreshPing = true,
        ) { _, _ -> AutoCloseable { } }.first()
      }

      assertThat(update).isEqualTo(FileBackedSessionChangeSet())
    }
  }

  @Test
  fun closesWatcherWhenCollectorStops() = runBlocking(Dispatchers.Default) {
    val closed = CompletableDeferred<Unit>()
    val updatesJob = launch {
      createFileBackedSessionChangeFlow(
        logger = logger<FileBackedSessionUpdatesFlowTest>(),
        watcherName = "test watcher",
      ) { _, _ -> AutoCloseable { closed.complete(Unit) } }.collect { }
    }

    try {
      delay(100.milliseconds)
      updatesJob.cancelAndJoin()
      withTimeout(5.seconds) {
        closed.await()
      }
    }
    finally {
      if (updatesJob.isActive) {
        updatesJob.cancelAndJoin()
      }
    }
  }
}
