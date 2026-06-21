// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.filewatch.impl

import com.intellij.agent.workbench.filewatch.impl.watchservice.AbstractWatchKey
import com.intellij.agent.workbench.filewatch.impl.watchservice.AbstractWatchService
import com.intellij.agent.workbench.filewatch.impl.watchservice.WatchablePath
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.nio.file.WatchEvent
import java.nio.file.WatchEvent.Modifier
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.nio.file.Watchable
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class DirectoryWatcherImplTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun watchThrowsWhenWatcherIsAlreadyClosed() {
    runBlocking(Dispatchers.IO) {
    val watcher = DirectoryWatcher(listOf(tempDir), EventRecorder())

    watcher.close()

    assertThatThrownBy { runBlocking { watcher.watch() } }
      .isInstanceOf(IllegalStateException::class.java)
    }
  }

  @Test
  fun watchReturnsWhenWatcherIsClosed() = runBlocking(Dispatchers.IO) {
    val watcher = DirectoryWatcher(listOf(tempDir), EventRecorder())

    val completed = CompletableDeferred<Unit>()
    val watchJob = launch {
      watcher.watch()
      completed.complete(Unit)
    }
    try {
      delay(100.milliseconds)
      watcher.close()

      withTimeout(5.seconds) {
        completed.await()
      }
    }
    finally {
      watchJob.cancelAndJoin()
    }
  }

  @Test
  fun deletingNestedFilesEmitsDeleteEvents() {
    runBlocking(Dispatchers.IO) {
      val parentDir = Files.createDirectory(tempDir.resolve("parent"))
      val childDir = Files.createDirectory(parentDir.resolve("child"))
      val firstFile = Files.createFile(parentDir.resolve("first.jsonl"))
      val secondFile = Files.createFile(childDir.resolve("second.jsonl"))
      val recorder = EventRecorder()
      val watcher = DirectoryWatcher(listOf(tempDir), recorder)
      val watchJob = launch { watcher.watch() }
      try {
        delay(100.milliseconds)
        Files.delete(secondFile)
        Files.delete(firstFile)

        recorder.awaitEvent { event -> event.eventType == DirectoryChangeEvent.EventType.DELETE && event.path == secondFile }
        recorder.awaitEvent { event -> event.eventType == DirectoryChangeEvent.EventType.DELETE && event.path == firstFile }
      }
      finally {
        watcher.close()
        watchJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun repeatedStartAndCloseDoesNotCrash() {
    repeat(3) {
      val watcher = DirectoryWatcher(listOf(tempDir), EventRecorder())
      watcher.close()
    }
  }

  @Test
  fun invalidatingOnlyWatchKeyFailsWatch() {
    runBlocking(Dispatchers.IO) {
      val watchService = TestWatchService(queueSize = 8)
      val watcher = DirectoryWatcher(
        listOf(tempDir),
        EventRecorder(),
        watchService,
        watchablePathFactory = { path -> WatchablePath(path) },
      )
      val failure = CompletableDeferred<Throwable>()
      val watchJob = launch {
        try {
          watcher.watch()
        }
        catch (t: Throwable) {
          failure.complete(t)
        }
      }
      try {
        val key = withTimeout(5.seconds) {
          watchService.awaitRegisteredKey()
        }

        key.cancel()
        key.signalOverflow(tempDir)

        val error = withTimeout(5.seconds) {
          failure.await()
        }

        assertThat(error)
          .isInstanceOf(IllegalStateException::class.java)
          .hasMessage("No more directories left to watch")
      }
      finally {
        watcher.close()
        watchJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun unsupportedFileTreeModifierFallbackWalksTreeOnce() {
    runBlocking(Dispatchers.IO) {
      val childDir = tempDir.resolve("child")
      val nestedDir = childDir.resolve("nested")
      val fileTreeVisitor = FakeFileTreeVisitor(listOf(tempDir, childDir, nestedDir))
      val watchService = FileTreeUnsupportedWatchService()
      val watcher = DirectoryWatcher(
        listOf(tempDir),
        EventRecorder(),
        watchService,
        fileTreeVisitor,
        watchablePathFactory = { path -> FakeWatchablePath(path, watchService) },
      )

      val completed = CompletableDeferred<Unit>()
      val watchJob = launch {
        watcher.watch()
        completed.complete(Unit)
      }
      try {
        withTimeout(5.seconds) {
          watchService.awaitRegisteredDirectories(3)
        }

        assertThat(fileTreeVisitor.visitCount.get()).isEqualTo(1)
        assertThat(watchService.registeredDirectories).containsExactly(tempDir, childDir, nestedDir)
      }
      finally {
        watcher.close()
        watchJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun creatingDirectoryDoesNotReportNewFilesAsModified() {
    runBlocking(Dispatchers.IO) {
      val createdDirectory = tempDir.resolve("created")
      val createdFile = createdDirectory.resolve("session.jsonl")
      val fileTreeVisitor = MutableFakeFileTreeVisitor(listOf(tempDir), emptyList())
      val watchService = FileTreeUnsupportedWatchService()
      val recorder = EventRecorder()
      val watcher = DirectoryWatcher(
        listOf(tempDir),
        recorder,
        watchService,
        fileTreeVisitor,
        watchablePathFactory = { path -> FakeWatchablePath(path, watchService) },
      )

      val watchJob = launch { watcher.watch() }
      try {
        withTimeout(5.seconds) {
          watchService.awaitRegisteredDirectories(1)
        }
        Files.createDirectory(createdDirectory)
        Files.writeString(createdFile, "created")
        fileTreeVisitor.directories = listOf(createdDirectory)
        fileTreeVisitor.files = listOf(createdFile)
        watchService.signal(tempDir, FakeWatchEvent(ENTRY_CREATE, 1, createdDirectory.fileName))

        recorder.awaitEvent { event -> event.eventType == DirectoryChangeEvent.EventType.CREATE && event.path == createdDirectory }
        recorder.awaitEvent { event -> event.eventType == DirectoryChangeEvent.EventType.CREATE && event.path == createdFile }

        assertThat(recorder.drainEvents().any { event ->
          event.eventType == DirectoryChangeEvent.EventType.MODIFY && event.path == createdFile
        }).isFalse()
      }
      finally {
        watcher.close()
        watchJob.cancelAndJoin()
      }
    }
  }

  @Test
  fun overflowOnlyEventsResignalWatchKeyOnReset() {
    val watchService = TestWatchService(queueSize = 1)
    val key = watchService.register(WatchablePath(tempDir), listOf(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY))

    key.signalEvent(ENTRY_CREATE, tempDir.resolve("file"))

    val firstEvents = key.pollEvents()

    assertThat(firstEvents.map { it.kind() }).containsExactly(ENTRY_CREATE)
    key.signalOverflow(tempDir)
    assertThat(key.reset()).isTrue()

    val secondEvents = watchService.poll()!!.pollEvents()

    assertThat(secondEvents.map { it.kind() }).containsExactly(OVERFLOW)
  }

}

private class TestWatchService(private val queueSize: Int) : AbstractWatchService() {
  @Volatile
  private var registeredKey: AbstractWatchKey? = null

  override fun register(watchable: WatchablePath, eventTypes: Iterable<WatchEvent.Kind<*>>): AbstractWatchKey {
    return AbstractWatchKey(this, watchable, eventTypes, queueSize).also { key ->
      registeredKey = key
    }
  }

  fun awaitRegisteredKey(): AbstractWatchKey {
    while (true) {
      registeredKey?.let { key -> return key }
      Thread.sleep(10)
    }
  }

}

private class FakeFileTreeVisitor(private val directories: List<Path>) : FileTreeVisitor {
  val visitCount = AtomicInteger()

  override fun recursiveVisitFiles(root: Path, directoryConsumer: (Path) -> Unit, fileConsumer: (Path) -> Unit) {
    visitCount.incrementAndGet()
    for (directory in directories) {
      directoryConsumer(directory)
    }
  }
}

private class MutableFakeFileTreeVisitor(
  @Volatile var directories: List<Path>,
  @Volatile var files: List<Path>,
) : FileTreeVisitor {
  override fun recursiveVisitFiles(root: Path, directoryConsumer: (Path) -> Unit, fileConsumer: (Path) -> Unit) {
    for (directory in directories) {
      directoryConsumer(directory)
    }
    for (file in files) {
      fileConsumer(file)
    }
  }
}

private class FakeWatchablePath(private val path: Path, private val watchService: FileTreeUnsupportedWatchService) : Watchable {
  override fun register(watcher: WatchService, events: Array<out WatchEvent.Kind<*>>, vararg modifiers: Modifier): WatchKey {
    check(watcher === watchService)
    if (modifiers.isNotEmpty()) {
      throw UnsupportedOperationException()
    }
    return watchService.register(path, events)
  }

  override fun register(watcher: WatchService, vararg events: WatchEvent.Kind<*>): WatchKey {
    return register(watcher, events)
  }
}

private class FileTreeUnsupportedWatchService : WatchService {
  private val keys = LinkedBlockingQueue<FileTreeUnsupportedWatchKey>()
  private val registered = ArrayList<Path>()

  val registeredDirectories: List<Path>
    get() = ArrayList(registered)

  fun awaitRegisteredDirectories(count: Int) {
    while (registered.size < count) {
      Thread.sleep(10)
    }
  }

  fun register(directory: Path, @Suppress("unused") events: Array<out WatchEvent.Kind<*>>): FileTreeUnsupportedWatchKey {
    registered.add(directory)
    return FileTreeUnsupportedWatchKey(directory, keys).also { key ->
      watchKeys[directory] = key
    }
  }

  private val watchKeys = HashMap<Path, FileTreeUnsupportedWatchKey>()

  fun signal(directory: Path, event: WatchEvent<*>) {
    watchKeys.getValue(directory).post(event)
  }

  override fun poll(): WatchKey? = keys.poll()

  override fun poll(timeout: Long, unit: TimeUnit): WatchKey? = keys.poll(timeout, unit)

  override fun take(): WatchKey = keys.take()

  override fun close() {
  }
}

private class FileTreeUnsupportedWatchKey(
  private val directory: Path,
  private val keys: LinkedBlockingQueue<FileTreeUnsupportedWatchKey>,
) : WatchKey {
  private val events = ArrayList<WatchEvent<*>>()
  private var pendingOverflowCount = 0
  private var signalled = false

  fun post(event: WatchEvent<*>) {
    events.add(event)
    signal()
  }

  private fun signal() {
    if (!signalled) {
      signalled = true
      keys.add(this)
    }
  }

  override fun isValid(): Boolean = true

  override fun pollEvents(): List<WatchEvent<*>> {
    val result = ArrayList(events)
    events.clear()
    if (pendingOverflowCount != 0) {
      result.add(FakeWatchEvent(OVERFLOW, pendingOverflowCount, null))
      pendingOverflowCount = 0
    }
    return result
  }

  override fun reset(): Boolean {
    signalled = false
    if (events.isNotEmpty() || pendingOverflowCount != 0) {
      signal()
    }
    return true
  }

  override fun cancel() {
  }

  override fun watchable(): Watchable = directory
}

private data class FakeWatchEvent<T>(
  private val kind: WatchEvent.Kind<T>,
  private val count: Int,
  private val context: T?,
) : WatchEvent<T> {
  override fun kind(): WatchEvent.Kind<T> = kind

  override fun count(): Int = count

  override fun context(): T? = context
}

private class EventRecorder : DirectoryChangeListener {
  private val events = LinkedBlockingQueue<DirectoryChangeEvent>()

  override suspend fun onEvent(event: DirectoryChangeEvent) {
    events.add(event)
  }

  suspend fun awaitEvent(predicate: (DirectoryChangeEvent) -> Boolean): DirectoryChangeEvent {
    return withTimeout(5.seconds) {
      lateinit var matchingEvent: DirectoryChangeEvent
      while (true) {
        val event = events.take()
        if (predicate(event)) {
          matchingEvent = event
          break
        }
      }
      matchingEvent
    }
  }

  fun drainEvents(): List<DirectoryChangeEvent> {
    val result = ArrayList<DirectoryChangeEvent>()
    events.drainTo(result)
    return result
  }
}
