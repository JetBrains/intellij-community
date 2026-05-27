// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.filewatch.impl.watchservice

import com.intellij.agent.workbench.filewatch.agentWorkbenchImmediateFileChangeFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class MacOSXListeningWatchServiceTest {
  @TempDir
  lateinit var tempDir: Path
  private val pendingEvents = ArrayList<WatchEvent<*>>()

  @Test
  fun createModifyDeleteEventsAreDeliveredByNativeMacWatcher() = runBlocking(Dispatchers.IO) {
    assumeMacOS()
    val watchService = createWatchService()
    val watchKey = WatchablePath(tempDir).register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)

    watchService.use {
      delay(100.milliseconds)
      val file = tempDir.resolve("session.jsonl")

      Files.writeString(file, "created")
      awaitEvent(watchService, watchKey, ENTRY_CREATE, file)

      Files.writeString(file, "modified")
      awaitEvent(watchService, watchKey, ENTRY_MODIFY, file)

      Files.delete(file)
      awaitEvent(watchService, watchKey, ENTRY_DELETE, file)
    }
  }

  @Test
  fun nestedFileDeleteIsDeliveredByNativeMacWatcher() = runBlocking(Dispatchers.IO) {
    assumeMacOS()
    val watchService = createWatchService()
    val watchKey = WatchablePath(tempDir).register(watchService, ENTRY_CREATE, ENTRY_DELETE)

    watchService.use {
      delay(100.milliseconds)
      val directory = Files.createDirectory(tempDir.resolve("session"))
      val file = directory.resolve("nested.jsonl")

      Files.writeString(file, "created")
      awaitEvent(watchService, watchKey, ENTRY_CREATE, file)

      Files.delete(file)
      awaitEvent(watchService, watchKey, ENTRY_DELETE, file)
    }
  }

  @Test
  fun immediateFileChangeFlowDeliversLongLivedAppendFdBeforeClose(): Unit = runBlocking(Dispatchers.IO) {
    // Codex keeps the rollout JSONL fd open for the entire session lifetime. FSEvents alone
    // withholds MODIFY events until the fd is closed, so active chat tabs subscribe to their
    // concrete session file with kqueue vnode events.
    assumeMacOS()
    val file = tempDir.resolve("rollout-long-lived.jsonl")
    Files.createFile(file)
    val expectedPath = file.toAbsolutePath().normalize()
    val changes = Channel<Path>(Channel.UNLIMITED)
    val watchJob = launch {
      agentWorkbenchImmediateFileChangeFlow(listOf(file)).collect { path ->
        changes.trySend(path)
      }
    }

    try {
      delay(200.milliseconds)
      Files.newOutputStream(file, StandardOpenOption.APPEND).use { stream ->
        stream.write("line1\n".toByteArray())
        stream.flush()
        assertThat(withTimeout(5.seconds) { changes.receive() }).isEqualTo(expectedPath)

        stream.write("line2\n".toByteArray())
        stream.flush()
        assertThat(withTimeout(5.seconds) { changes.receive() }).isEqualTo(expectedPath)
      }
    }
    finally {
      watchJob.cancelAndJoin()
      changes.close()
    }
  }

  @Test
  fun repeatedNativeStartAndCloseDoesNotCrash() {
    assumeMacOS()
    repeat(3) {
      createWatchService().use { watchService ->
        WatchablePath(tempDir).register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
      }
    }
  }

  private fun createWatchService(): MacOSXListeningWatchService {
    return MacOSXListeningWatchService(object : MacOSXListeningWatchService.Config {
      override val latency: Double = 0.1
    })
  }

  private fun awaitEvent(
    watchService: MacOSXListeningWatchService,
    expectedKey: WatchKey,
    expectedKind: WatchEvent.Kind<Path>,
    expectedPath: Path,
  ) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (System.nanoTime() < deadline) {
      val pendingEvent = pendingEvents.findAndRemove(expectedKind, expectedPath)
      if (pendingEvent != null) {
        assertThat(pendingEvent.context()).isEqualTo(expectedPath)
        return
      }
      val key = watchService.poll(100, TimeUnit.MILLISECONDS) ?: continue
      assertThat(key).isSameAs(expectedKey)
      val events = key.pollEvents()
      val event = events.firstOrNull { event -> event.kind() === expectedKind && event.context() == expectedPath }
      if (event != null) {
        assertThat(event.context()).isEqualTo(expectedPath)
        pendingEvents.addAll(events - event)
        key.reset()
        return
      }
      pendingEvents.addAll(events)
      key.reset()
    }
    throw AssertionError("Expected $expectedKind event for $expectedPath")
  }

  private fun ArrayList<WatchEvent<*>>.findAndRemove(kind: WatchEvent.Kind<Path>, path: Path): WatchEvent<*>? {
    val iterator = iterator()
    while (iterator.hasNext()) {
      val event = iterator.next()
      if (event.kind() === kind && event.context() == path) {
        iterator.remove()
        return event
      }
    }
    return null
  }

  private fun assumeMacOS() {
    assumeTrue(System.getProperty("os.name").contains("mac", ignoreCase = true))
  }
}
