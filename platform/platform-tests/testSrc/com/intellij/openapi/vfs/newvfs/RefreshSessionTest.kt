// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

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
