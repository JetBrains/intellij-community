// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.filewatch.impl.watchservice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
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
  fun longLivedAppendFdDoesNotDeliverModifyEventsUntilClose(): Unit = runBlocking(Dispatchers.IO) {
    // Regression marker for the codex live-status bug: macOS FSEvents withholds MODIFY events
    // for writes performed through a long-lived O_APPEND fd. The events only flush when the fd
    // is closed. fsync/FileChannel.force(true) between writes does not break the silence.
    //
    // Codex keeps the rollout JSONL fd open for the entire session lifetime, so during a long
    // in-flight turn no MODIFY events reach the IDE and live activity hints cannot update. If
    // this test ever starts failing, FSEvents has changed semantics and the workaround
    // (opportunistic mtime poll for active rollout files) can be reconsidered.
    assumeMacOS()
    val watchService = createWatchService()
    WatchablePath(tempDir).register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)

    watchService.use {
      delay(200.milliseconds)
      val file = tempDir.resolve("rollout-long-lived.jsonl")
      Files.createFile(file)
      assertThat(collectKinds(watchService)).contains(ENTRY_CREATE)

      Files.newOutputStream(file, StandardOpenOption.APPEND).use { stream ->
        stream.write("line1\n".toByteArray())
        stream.flush()
        delay(800.milliseconds)
        assertThat(collectKinds(watchService))
          .describedAs("FSEvents must not deliver MODIFY for write through long-lived O_APPEND fd")
          .doesNotContain(ENTRY_MODIFY)

        stream.write("line2\n".toByteArray())
        stream.flush()
        delay(800.milliseconds)
        assertThat(collectKinds(watchService))
          .describedAs("FSEvents must not deliver MODIFY for second write through long-lived O_APPEND fd")
          .doesNotContain(ENTRY_MODIFY)
      }
      // Closing the fd finally flushes a single MODIFY for the accumulated writes.
      delay(800.milliseconds)
      assertThat(collectKinds(watchService))
        .describedAs("FSEvents delivers a flushed MODIFY only after the writing fd closes")
        .contains(ENTRY_MODIFY)
    }
  }

  private fun collectKinds(watchService: MacOSXListeningWatchService): List<WatchEvent.Kind<*>> {
    val key = watchService.poll(50, TimeUnit.MILLISECONDS) ?: return emptyList()
    val events = key.pollEvents()
    key.reset()
    return events.map { it.kind() }
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
