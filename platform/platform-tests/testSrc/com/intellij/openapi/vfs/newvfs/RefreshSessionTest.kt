// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.vfs.AfterEventShouldBeFiredBeforeOtherListeners
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile
import com.intellij.testFramework.TestObservation
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.useProjectAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

@TestApplication
class RefreshSessionTest {
  @Test
  @RegistryKey("vfs.refresh.use.background.write.action", "false")
  fun `synchronous refresh applies events scanned by an earlier refresh before returning`(): Unit = timeoutRunBlocking {
    val fileSystem = CaseSensitiveTempFileSystem()
    val parent = createTempDirectory(fileSystem)
    try {
      parent.children
      fileSystem.createIfNotExists(parent, "child.txt")

      val synchronousSession = RefreshQueue.getInstance().createSession(false, true, null).apply {
        addFile(parent)
      }
      (parent as NewVirtualFile).markDirtyRecursively()

      val pendingSession = RefreshQueue.getInstance().createSession(true, true, null).apply {
        addFile(parent)
      }
      // Split the first refresh at the scan/apply boundary to reproduce the race without timing dependencies
      val pendingEvents = RefreshQueueImpl.scanForTest(pendingSession)
      assertThat(pendingEvents).isNotEmpty()

      withContext(Dispatchers.EDT) {
        RefreshQueue.getInstance().processEvents(true, pendingEvents.toList())
        synchronousSession.launch()

        assertThat(parent.findChild("child.txt")).isNotNull()
      }
    }
    finally {
      deleteTempDirectory(parent)
    }
  }

  @Test
  fun `synchronous refresh scans a directory that another session scans while the refresh is running`(
    @TempDir tempDir: Path,
  ): Unit = timeoutRunBlocking {
    val parentPath = Files.createDirectory(tempDir.resolve("parent"))
    val dirPath = Files.createDirectory(parentPath.resolve("dir"))
    val parent = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(parentPath))
    val dir = checkNotNull(parent.findChild("dir"))
    parent.children
    dir.children
    Files.createFile(dirPath.resolve("child.txt"))

    val pendingSession = RefreshQueue.getInstance().createSession(true, true, null).apply {
      addFile(dir)
    }
    val synchronousSession = RefreshQueue.getInstance().createSession(false, true, null).apply {
      addFile(parent)
    }
    (parent as NewVirtualFile).markDirtyRecursively()

    var pendingScanned = false
    RefreshQueueImpl.setTestListener { file ->
      // The synchronous refresh is about to list `parent`. Another session scans `dir` right before, so `dir` is already clean,
      // but its events are not applied yet, when the synchronous refresh gets to it
      if (file == parent && !pendingScanned) {
        pendingScanned = true
        RefreshQueueImpl.scanForTest(pendingSession)
      }
    }
    try {
      withContext(Dispatchers.EDT) {
        synchronousSession.launch()

        assertThat(pendingScanned).describedAs("the test hook must have scanned `dir` in the middle of the synchronous refresh").isTrue()
        assertThat(dir.findChild("child.txt"))
          .describedAs("the synchronous refresh must scan `dir` itself, as the events of the other session are not applied yet")
          .isNotNull()
      }
    }
    finally {
      RefreshQueueImpl.setTestListener(null)
    }
  }

  @Test
  fun `synchronous refresh from a background thread scans the files that another session has not applied yet`(): Unit = timeoutRunBlocking {
    val dir = createDirectoryWithNewChild()
    try {
      val pendingSession = RefreshQueue.getInstance().createSession(true, true, null).apply { addFile(dir) }
      val synchronousSession = RefreshQueue.getInstance().createSession(false, true, null).apply { addFile(dir) }
      // The events of the first session are never applied: they are still on their way, when the synchronous refresh starts
      RefreshQueueImpl.scanForTest(pendingSession)
      try {
        // The test thread is not EDT and holds no write lock: the session goes through the refresh queue, and the thread waits for it
        synchronousSession.launch()

        assertThat(dir.findChild("child.txt"))
          .describedAs("the synchronous refresh must scan `dir` itself, as the events of the other session are not applied yet")
          .isNotNull()
      }
      finally {
        RefreshQueueImpl.releaseScannedFilesForTest(pendingSession)
      }
    }
    finally {
      deleteTempDirectory(dir)
    }
  }

  @Test
  fun `asynchronous refresh does not scan the files that another session has not applied yet`(): Unit = timeoutRunBlocking {
    val dir = createDirectoryWithNewChild()
    try {
      val pendingSession = RefreshQueue.getInstance().createSession(true, true, null).apply { addFile(dir) }
      val asynchronousSession = RefreshQueue.getInstance().createSession(true, true, null).apply { addFile(dir) }
      val synchronousSession = RefreshQueue.getInstance().createSession(false, true, null).apply { addFile(dir) }
      RefreshQueueImpl.scanForTest(pendingSession)
      try {
        assertThat(RefreshQueueImpl.scanForTest(asynchronousSession))
          .describedAs("an asynchronous refresh promises nothing about the up-to-date VFS, and the events of the other session are on their way")
          .isEmpty()
        assertThat(RefreshQueueImpl.scanForTest(synchronousSession))
          .describedAs("control: a synchronous refresh scans the same directory")
          .isNotEmpty()
      }
      finally {
        RefreshQueueImpl.releaseScannedFilesForTest(pendingSession)
        RefreshQueueImpl.releaseScannedFilesForTest(asynchronousSession)
        RefreshQueueImpl.releaseScannedFilesForTest(synchronousSession)
      }
    }
    finally {
      deleteTempDirectory(dir)
    }
  }

  @Test
  fun `shallow synchronous refresh scans a child file that another session has not applied yet`(@TempDir tempDir: Path) {
    val dirPath = Files.createDirectory(tempDir.resolve("parent"))
    val filePath = Files.writeString(dirPath.resolve("file.txt"), "old")
    val dir = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dirPath)) as NewVirtualFile
    val file = checkNotNull(dir.findChild("file.txt"))
    dir.children
    // The length differs, so a scan reports a content change
    Files.writeString(filePath, "new and longer content")
    dir.markDirtyRecursively()
    val pendingSession = RefreshQueue.getInstance().createSession(true, true, null).apply { addFile(dir) }
    val shallowSession = RefreshQueue.getInstance().createSession(false, false, null).apply { addFile(dir) }
    RefreshQueueImpl.scanForTest(pendingSession)
    try {
      val events = RefreshQueueImpl.scanForTest(shallowSession)

      assertThat(events.filterIsInstance<VFileContentChangeEvent>().map { it.file })
        .describedAs("a shallow synchronous refresh checks the children of the directory, and the file is still in flight")
        .contains(file)
    }
    finally {
      RefreshQueueImpl.releaseScannedFilesForTest(pendingSession)
      RefreshQueueImpl.releaseScannedFilesForTest(shallowSession)
    }
  }

  @Test
  fun `files are not actualizing after a refresh on EDT has applied its events`(): Unit = timeoutRunBlocking {
    val dir = createDirectoryWithNewChild()
    try {
      val session = RefreshQueue.getInstance().createSession(false, true, null).apply { addFile(dir) }

      withContext(Dispatchers.EDT) {
        session.launch()
      }

      assertThat(dir.findChild("child.txt")).describedAs("the session has applied the create event").isNotNull()
      assertThat(isActualizing(dir)).describedAs("the events are applied, so the files are not in flight any more").isFalse()
    }
    finally {
      deleteTempDirectory(dir)
    }
  }

  @Test
  fun `files are not actualizing after a refresh on a background thread has applied its events`(): Unit = timeoutRunBlocking {
    val dir = createDirectoryWithNewChild()
    try {
      val session = RefreshQueue.getInstance().createSession(false, true, null).apply { addFile(dir) }

      session.launch()

      assertThat(dir.findChild("child.txt")).describedAs("the session has applied the create event").isNotNull()
      assertThat(isActualizing(dir)).describedAs("the events are applied, so the files are not in flight any more").isFalse()
    }
    finally {
      deleteTempDirectory(dir)
    }
  }

  @Test
  fun `files are not actualizing after a refresh that found no events`(): Unit = timeoutRunBlocking {
    val dir = createDirectoryWithNewChild(withChild = false)
    try {
      val session = RefreshQueue.getInstance().createSession(false, true, null).apply { addFile(dir) }

      withContext(Dispatchers.EDT) {
        session.launch()
      }

      assertThat(isActualizing(dir))
        .describedAs("the scan has cleaned the directory, and nothing is left to apply, so the directory must not stay in flight")
        .isFalse()
    }
    finally {
      deleteTempDirectory(dir)
    }
  }

  /**
   * It is a race between the scan that cleans the directory (adds it to the registry, then cleans the flag) and the synchronous scan
   * (reads the flag, then the registry). The synchronous scan must find the directory in any interleaving. A broken order of
   * the operations would make the test fail only sometimes, so this is a guard, not a proof
   */
  @Test
  fun `synchronous scan finds a directory that another scan cleans at the same moment`(): Unit = timeoutRunBlocking(timeout = 60.seconds) {
    val dir = createDirectoryWithNewChild()
    val executor = Executors.newFixedThreadPool(2)
    try {
      repeat(200) { iteration ->
        dir.markDirtyRecursively()
        val pendingSession = RefreshQueue.getInstance().createSession(true, true, null).apply { addFile(dir) }
        val synchronousSession = RefreshQueue.getInstance().createSession(false, true, null).apply { addFile(dir) }
        val start = CountDownLatch(1)
        val pending = executor.submit(Callable { start.await(); RefreshQueueImpl.scanForTest(pendingSession) })
        val synchronous = executor.submit(Callable { start.await(); RefreshQueueImpl.scanForTest(synchronousSession) })
        start.countDown()
        try {
          pending.get(10, TimeUnit.SECONDS)
          assertThat(synchronous.get(10, TimeUnit.SECONDS).filterIsInstance<VFileCreateEvent>())
            .describedAs("iteration $iteration: the synchronous scan must create `child.txt`, whatever the other scan is doing")
            .isNotEmpty()
        }
        finally {
          RefreshQueueImpl.releaseScannedFilesForTest(pendingSession)
          RefreshQueueImpl.releaseScannedFilesForTest(synchronousSession)
        }
      }
    }
    finally {
      executor.shutdownNow()
      deleteTempDirectory(dir)
    }
  }

  /**
   * The directory is dirty, and VFS has listed it before `child.txt` appeared in the file system, so a scan creates the child.
   * A temp file system is used, because for the local one an inoperational file watcher marks the roots of a session dirty anyway,
   * see `LocalFileSystemImpl.markSuspiciousFilesDirty`
   */
  private suspend fun createDirectoryWithNewChild(withChild: Boolean = true): NewVirtualFile {
    val fileSystem = CaseSensitiveTempFileSystem()
    val dir = createTempDirectory(fileSystem) as NewVirtualFile
    dir.children
    if (withChild) fileSystem.createIfNotExists(dir, "child.txt")
    dir.markDirtyRecursively()
    return dir
  }

  private fun isActualizing(file: NewVirtualFile): Boolean =
    (RefreshQueue.getInstance() as RefreshQueueImpl).inFlightScans.isActualizing(file.id)

  @Test
  fun `session loads multiple new files`(@TempDir tempDir: Path) {
    val parentPath = Files.createDirectory(tempDir.resolve("parent"))
    val parent = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(parentPath))
    parent.children

    Files.createFile(parentPath.resolve("one.txt"))
    Files.createFile(parentPath.resolve("two.txt"))
    assertThat(parent.findChild("one.txt")).isNull()
    assertThat(parent.findChild("two.txt")).isNull()

    RefreshQueue.getInstance().createSession(false, false, null).apply {
      addNewChildren(parent, listOf("one.txt", "two.txt"))
    }.launch()

    assertThat(parent.findChild("one.txt")).isNotNull()
    assertThat(parent.findChild("two.txt")).isNotNull()
  }

  @Test
  fun `session rejects multiple requestors for the same parent`(@TempDir tempDir: Path) {
    val parentPath = Files.createDirectory(tempDir.resolve("parent"))
    val sourcePath = Files.createFile(tempDir.resolve("source.txt"))
    val parent = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(parentPath))
    val source = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(sourcePath))
    val session = RefreshQueue.getInstance().createSession(false, false, null)

    session.addCopyFile(parent, "one.txt", source, Any())

    assertThrows<IllegalArgumentException> {
      session.addCopyFile(parent, "two.txt", source, Any())
    }
  }

  @Test
  fun `session rejects multiple source files for the same parent`(@TempDir tempDir: Path) {
    val parentPath = Files.createDirectory(tempDir.resolve("parent"))
    val firstSourcePath = Files.createFile(tempDir.resolve("first.txt"))
    val secondSourcePath = Files.createFile(tempDir.resolve("second.txt"))
    val parent = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(parentPath))
    val firstSource = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(firstSourcePath))
    val secondSource = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(secondSourcePath))
    val requestor = Any()
    val session = RefreshQueue.getInstance().createSession(false, false, null)

    session.addCopyFile(parent, "one.txt", firstSource, requestor)

    assertThrows<IllegalArgumentException> {
      session.addCopyFile(parent, "two.txt", secondSource, requestor)
    }
  }


  @Test
  fun `session rejects mixing created and copied children`(@TempDir tempDir: Path) {
    val parentPath = Files.createDirectory(tempDir.resolve("parent"))
    val destinationPath = Files.createDirectory(tempDir.resolve("destination"))
    val parent = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(parentPath))
    val destination = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(destinationPath))
    val requestor = Any()
    val session = RefreshQueue.getInstance().createSession(false, false, null)

    session.addNewChildren(parent, listOf("one.txt"))

    assertThrows<IllegalArgumentException> {
      session.addCopyFile(destination, "two.txt", parent, requestor)
    }
  }

  @Test
  fun `session deduplicates events from parent and selected child refresh`(
    @TempDir tempDir: Path,
    @TestDisposable disposable: Disposable,
  ) {
    val parentPath = Files.createDirectory(tempDir.resolve("parent"))
    val parent = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(parentPath))
    parent.children

    Files.createFile(parentPath.resolve("one.txt"))
    assertThat(parent.findChild("one.txt")).isNull()

    val createEventCount = AtomicInteger()
    VirtualFileManager.getInstance().addAsyncFileListener({ events ->
      createEventCount.addAndGet(events.count { it is VFileCreateEvent && it.parent == parent && it.childName == "one.txt" })
      null
    }, disposable)

    RefreshQueue.getInstance().createSession(false, false, null).apply {
      addAllFiles(parent)
      addNewChildren(parent, listOf("one.txt"))
    }.launch()

    assertThat(createEventCount.get()).isEqualTo(1)
    assertThat(parent.findChild("one.txt")).isNotNull()
  }

  @Test
  fun `session reports copied children and preloads the scanned subtree`(
    @TempDir tempDir: Path,
    @TestDisposable disposable: Disposable,
  ): Unit = runBlocking {
    Files.createDirectories(tempDir.resolve(".idea"))
    val sourcePath = Files.createDirectories(tempDir.resolve("source/nested"))
    Files.writeString(sourcePath.resolve("file.txt"), "content")
    val destinationPath = tempDir.resolve("renamed")

    ProjectUtil.openOrImportAsync(tempDir)!!.useProjectAsync { project ->
      TestObservation.awaitConfiguration(project)
      val parent = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempDir))
      parent.children
      val source = checkNotNull(parent.findChild("source"))
      source.children

      Files.createDirectories(destinationPath.resolve("nested"))
      Files.writeString(destinationPath.resolve("nested/file.txt"), "content")
      assertThat(parent.findChild("renamed")).isNull()

      val copyEvent = AtomicReference<VFileCopyEvent?>()
      val requestor = Any()
      val afterVfsChangeCalled = AtomicBoolean()
      VirtualFileManager.getInstance().addAsyncFileListener({ events ->
        val copyEvents = events.filterIsInstance<VFileCopyEvent>()
        assertThat(copyEvents).hasSize(1)
        assertThat(events.filterIsInstance<VFileCreateEvent>()).isEmpty()
        copyEvent.set(copyEvents.single())
        object : AsyncFileListener.ChangeApplier, AfterEventShouldBeFiredBeforeOtherListeners {
          override fun afterVfsChange() {
            afterVfsChangeCalled.set(true)
            val copied = checkNotNull((parent as NewVirtualFile).findChildIfCached("renamed"))
            assertThat(copied.allChildrenLoaded()).isTrue()
            val nested = checkNotNull(copied.findChildIfCached("nested"))
            assertThat(nested.allChildrenLoaded()).isTrue()
            assertThat(nested.findChildIfCached("file.txt")).isNotNull()
          }
        }
      }, disposable)

      RefreshQueue.getInstance().createSession(false, false, null).apply {
        addCopyFile(parent, "renamed", source, requestor)
      }.launch()

      assertThat(afterVfsChangeCalled).isTrue()
      val event = checkNotNull(copyEvent.get())
      assertThat(event.file).isSameAs(source)
      assertThat(event.requestor).isSameAs(requestor)
      assertThat(event.attributes).isNotNull()
      assertThat(event.attributes!!.isDirectory).isTrue()
      assertThat(event.children).hasSize(1)
      assertThat(event.isAllChildren).isTrue()
    }
  }

  @Test
  fun `session adds children to a case-insensitive file system`(): Unit = runBlocking {
    val fileSystem = CaseInsensitiveTempFileSystem()
    val parent = createTempDirectory(fileSystem)
    try {
      assertThat(parent.isCaseSensitive).isFalse()
      parent.children
      fileSystem.createIfNotExists(parent, "child.txt")
      assertThat(parent.findChild("child.txt")).isNull()
      assertThat(parent.findChild("CHILD.TXT")).isNull()

      RefreshQueue.getInstance().createSession(false, false, null).apply {
        addNewChildren(parent, listOf("child.txt", "CHILD.TXT"))
      }.launch()

      val child = checkNotNull(parent.findChild("child.txt"))
      assertThat(parent.findChild("CHILD.TXT")).isEqualTo(child)
      assertThat(parent.children).containsExactly(child)
    }
    finally {
      deleteTempDirectory(parent)
    }
  }

  @Test
  fun `session adds children to a case-sensitive file system`(): Unit = runBlocking {
    val fileSystem = CaseSensitiveTempFileSystem()
    val parent = createTempDirectory(fileSystem)
    try {
      assertThat(parent.isCaseSensitive).isTrue()
      parent.children
      fileSystem.createIfNotExists(parent, "child.txt")
      fileSystem.createIfNotExists(parent, "CHILD.TXT")
      assertThat(parent.findChild("child.txt")).isNull()
      assertThat(parent.findChild("CHILD.TXT")).isNull()

      RefreshQueue.getInstance().createSession(false, false, null).apply {
        addNewChildren(parent, listOf("child.txt", "CHILD.TXT"))
      }.launch()

      val child = checkNotNull(parent.findChild("child.txt"))
      val upperCaseChild = checkNotNull(parent.findChild("CHILD.TXT"))
      assertThat(upperCaseChild).isNotSameAs(child)
      assertThat(parent.children).containsExactlyInAnyOrder(child, upperCaseChild)
    }
    finally {
      deleteTempDirectory(parent)
    }
  }

  private suspend fun createTempDirectory(fileSystem: TempFileSystem): VirtualFile {
    val root = checkNotNull(ManagingFS.getInstance().findRoot("/", fileSystem))
    return writeAction {
      root.createChildDirectory(this@RefreshSessionTest, "refresh-session-${System.nanoTime()}")
    }
  }

  private suspend fun deleteTempDirectory(directory: VirtualFile) {
    writeAction {
      if (directory.isValid) {
        directory.delete(this@RefreshSessionTest)
      }
    }
  }

  @Test
  fun `session ignores files that do not exist`(@TempDir tempDir: Path) {
    val parentPath = Files.createDirectory(tempDir.resolve("parent"))
    val parent = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(parentPath))
    parent.children

    RefreshQueue.getInstance().createSession(false, false, null).apply {
      addNewChildren(parent, listOf("missing.txt"))
    }.launch()

    assertThat(parent.findChild("missing.txt")).isNull()
  }
}

private class CaseSensitiveTempFileSystem : TempFileSystem() {
  private val protocol = "refresh-session-case-sensitive-${System.identityHashCode(this)}"

  override fun getProtocol(): String = protocol

  override fun isCaseSensitive(): Boolean = true
}

private class CaseInsensitiveTempFileSystem : TempFileSystem() {
  private val protocol = "refresh-session-case-insensitive-${System.identityHashCode(this)}"

  override fun getProtocol(): String = protocol

  override fun isCaseSensitive(): Boolean = false

  override fun createIfNotExists(parent: VirtualFile, name: String) {
    if (list(parent).none { it.equals(name, ignoreCase = true) }) {
      super.createIfNotExists(parent, name)
    }
  }

  override fun getAttributes(file: VirtualFile): FileAttributes? {
    return super.getAttributes(findFileWithActualCase(file))?.withCaseSensitivity(FileAttributes.CaseSensitivity.INSENSITIVE)
  }

  override fun getCanonicallyCasedName(file: VirtualFile): String {
    val parent = file.parent ?: return super.getCanonicallyCasedName(file)
    return list(parent).firstOrNull { it.equals(file.name, ignoreCase = true) }
           ?: super.getCanonicallyCasedName(file)
  }

  private fun findFileWithActualCase(file: VirtualFile): VirtualFile {
    val parent = file.parent ?: return file
    val actualName = list(parent).firstOrNull { it.equals(file.name, ignoreCase = true) } ?: return file
    return if (actualName == file.name) file else FakeVirtualFile(parent, actualName)
  }
}
