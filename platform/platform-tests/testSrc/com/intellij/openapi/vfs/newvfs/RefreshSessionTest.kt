// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.vfs.AfterEventShouldBeFiredBeforeOtherListeners
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile
import com.intellij.testFramework.TestObservation
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.useProjectAsync
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@TestApplication
class RefreshSessionTest {
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
