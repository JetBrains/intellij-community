// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.writeText

@TestApplication
class VfsImplUtilForceRefreshTest {
  @TempDir
  lateinit var tempDir: Path

  @TestDisposable
  lateinit var disposable: Disposable

  /**
   * A rewrite that keeps the length and the timestamp is invisible to a plain refresh and visible to a forced one.
   */
  @Test
  fun `synchronous refresh reads a rewrite with the same length and timestamp`() {
    val file = createCachedFile("main.txt", "before")
    rewriteKeepingLengthAndTimestamp(file, "after!")

    file.refresh(false, false)
    assertThat(VfsUtilCore.loadText(file)).isEqualTo("before")

    VfsImplUtil.forceRefresh(false, listOf(file))
    assertThat(VfsUtilCore.loadText(file)).isEqualTo("after!")
  }

  /**
   * Several files are refreshed in one session, so listeners get one batch with a content change for each of them.
   */
  @Test
  fun `several files are refreshed in one batch`() {
    val first = createCachedFile("first.txt", "before")
    val second = createCachedFile("second.txt", "before")
    rewriteKeepingLengthAndTimestamp(first, "after1")
    rewriteKeepingLengthAndTimestamp(second, "after2")
    val batches = recordBatches()

    VfsImplUtil.forceRefresh(false, listOf(first, second))

    assertThat(batches).hasSize(1)
    assertThat(batches.single().map { (it as VFileContentChangeEvent).file }).containsExactlyInAnyOrder(first, second)
    assertThat(VfsUtilCore.loadText(first)).isEqualTo("after1")
    assertThat(VfsUtilCore.loadText(second)).isEqualTo("after2")
  }

  /**
   * An asynchronous refresh applies the rewrite later, in a background write action.
   */
  @Test
  fun `asynchronous refresh reads the rewrite later`() {
    val file = createCachedFile("main.txt", "before")
    rewriteKeepingLengthAndTimestamp(file, "after!")

    VfsImplUtil.forceRefresh(true, listOf(file))

    timeoutRunBlocking {
      waitUntil("the asynchronous refresh reads the rewrite") {
        VfsUtilCore.loadText(file) == "after!"
      }
    }
  }

  /**
   * An empty collection fires no events.
   */
  @Test
  fun `empty collection fires nothing`() {
    val batches = recordBatches()

    VfsImplUtil.forceRefresh(false, emptyList())

    assertThat(batches).isEmpty()
  }

  /**
   * [VfsImplUtil.forceSyncRefresh] keeps its behavior after it delegates to [VfsImplUtil.forceRefresh].
   */
  @Test
  fun `forceSyncRefresh reads a rewrite with the same length and timestamp`() {
    val file = createCachedFile("main.txt", "before")
    rewriteKeepingLengthAndTimestamp(file, "after!")

    VfsImplUtil.forceSyncRefresh(file)

    assertThat(VfsUtilCore.loadText(file)).isEqualTo("after!")
  }

  /**
   * Creates a file and loads its content into the VFS cache, as for a file the IDE has read.
   */
  private fun createCachedFile(name: String, text: String): VirtualFile {
    val path = tempDir.resolve(name)
    path.writeText(text)
    val file = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path))
    assertThat(VfsUtilCore.loadText(file)).isEqualTo(text)
    return file
  }

  /**
   * Writes [text] of the same length over [file] and restores its timestamp, as two writes within the timestamp
   * granularity of the file system do.
   */
  private fun rewriteKeepingLengthAndTimestamp(file: VirtualFile, text: String) {
    val path = file.toNioPath()
    val timestamp = Files.getLastModifiedTime(path)
    check(Files.size(path) == text.length.toLong())
    path.writeText(text)
    Files.setLastModifiedTime(path, timestamp)
  }

  /**
   * Records each batch of VFS events that listeners get, limited to the events under [tempDir], so a refresh elsewhere
   * in the test application does not add a batch.
   */
  private fun recordBatches(): List<List<VFileEvent>> {
    val root = FileUtil.toSystemIndependentName(tempDir.toString())
    val batches = CopyOnWriteArrayList<List<VFileEvent>>()
    VirtualFileManager.getInstance().addAsyncFileListener({ events ->
      val ours = events.filter { FileUtil.startsWith(it.path, root) }
      if (ours.isNotEmpty()) {
        batches.add(ours)
      }
      null
    }, disposable)
    return batches
  }
}
