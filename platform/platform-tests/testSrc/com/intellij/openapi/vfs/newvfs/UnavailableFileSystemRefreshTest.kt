// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile
import com.intellij.openapi.vfs.newvfs.persistent.BatchingFileSystem
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * A refresh keeps a file whose file system cannot answer, keeps it dirty, and keeps the chain from the root to it dirty.
 * The fixture is a [BatchingFileSystem], as `LocalFileSystemImpl` is.
 */
@TestApplication
class UnavailableFileSystemRefreshTest {
  private val fs = UnavailableTempFileSystem()
  private lateinit var dir: NewVirtualFile

  @BeforeEach
  fun createDirectory(): Unit = runBlocking {
    val root = checkNotNull(ManagingFS.getInstance().findRoot("/", fs))
    dir = writeAction {
      root.createChildDirectory(this, "dir").also { it.createChildData(this, "a.txt") } as NewVirtualFile
    }
  }

  @Test
  fun `a refresh keeps the files while the file system is unavailable`() {
    val file = checkNotNull(dir.findChild("a.txt"))
    fs.unavailable = { true }

    refresh(dir)

    assertThat(file.isValid).isTrue()
    assertThat(dir.findChild("a.txt")).isEqualTo(file)
    assertThat(dir.isDirty).isTrue()
  }

  @Test
  fun `the next refresh after the outage applies the changes`() {
    val file = checkNotNull(dir.findChild("a.txt"))
    fs.unavailable = { true }
    refresh(dir)
    fs.createIfNotExists(dir, "b.txt")
    fs.deleteFile(this, file)

    fs.unavailable = { false }
    refresh(dir)

    assertThat(dir.children.map { it.name }).containsExactly("b.txt")
    assertThat(file.isValid).isFalse()
  }

  @Test
  fun `a failed subdirectory keeps its chain dirty and the sibling clean`(): Unit = runBlocking {
    val (sub, sibling) = writeAction {
      listOf("sub", "sibling").map { name ->
        dir.createChildDirectory(this, name).also { it.createChildData(this, "x.txt") } as NewVirtualFile
      }
    }
    fs.unavailable = { it.path == sub.path }

    refresh(dir)

    assertThat(sub.findChild("x.txt")).isNotNull()
    assertThat(dir.isDirty).isTrue()
    assertThat(sub.isDirty).isTrue()
    assertThat(sibling.isDirty).isFalse()
  }

  @Test
  fun `a partial scan keeps the cached child and applies a later deletion`() {
    val root = checkNotNull(ManagingFS.getInstance().findRoot("/", fs))
    // the file system creates the files without VFS events, so the VFS discovers them lazily
    fs.createIfNotExists(fs.createChildDirectory(this, root, "p"), "x.txt")
    val p = checkNotNull(root.findChild("p"))
    val x = checkNotNull(p.findChild("x.txt"))
    check(!p.allChildrenLoaded())
    fs.unavailable = { it.path == p.path }

    refresh(p)

    assertThat(x.isValid).isTrue()
    assertThat(p.findChild("x.txt")).isEqualTo(x)
    assertThat(p.isDirty).isTrue()

    fs.deleteFile(this, x)
    fs.unavailable = { false }
    refresh(p)

    assertThat(x.isValid).isFalse()
  }

  @Test
  @Suppress("SuspiciousPackagePrivateAccess")
  fun `a parent scan does not clear the dirty state of a skipped child`(): Unit = timeoutRunBlocking {
    val (sub, sibling) = writeAction {
      listOf("sub", "sibling").map { name ->
        dir.createChildDirectory(this, name).also { it.createChildData(this, "x.txt") } as NewVirtualFile
      }
    }
    fs.unavailable = { it.path == sub.path }

    // One worker (the write-intent lock), in queue order: `sub` fails and is skipped; `dir` is scanned, marks itself clean
    // and queues `sub` again; the second pass of `sub` succeeds (the hook ended the outage) and marks it clean. Only a
    // re-dirty after the scan leaves `sub` and `dir` dirty.
    val events = writeIntentReadAction {
      val scanThread = Thread.currentThread()
      fs.beforeThrow = {
        assertThat(Thread.currentThread()).isSameAs(scanThread)
        fs.unavailable = { false }
      }
      RefreshWorker(listOf(sub, dir), true).scan()
    }

    assertThat(events).isEmpty()
    assertThat(dir.isDirty).isTrue()
    assertThat(sub.isDirty).isTrue()
    assertThat(sibling.isDirty).isFalse()
  }

  private fun refresh(file: VirtualFile) {
    RefreshQueue.getInstance().createSession(false, true, null).apply { addFile(file) }.launch()
  }
}

/**
 * [unavailable] selects the directories whose content cannot be read: the stat of a child and the listing fail.
 * The strict reads throw. The lenient reads answer "absent" and "empty", as `LocalFileSystemImpl` does.
 */
private class UnavailableTempFileSystem : TempFileSystem(), UnavailabilityAwareFileSystem, BatchingFileSystem {
  private val protocol = "unavailable-${System.identityHashCode(this)}"

  @Volatile
  var unavailable: (VirtualFile) -> Boolean = { false }

  /** Runs right before a strict read throws, with the unavailable directory. */
  var beforeThrow: (VirtualFile) -> Unit = {}

  override fun getProtocol(): String = protocol

  override fun getAttributes(file: VirtualFile): FileAttributes? =
    if (isInUnavailable(file)) null else super.getAttributes(file)

  override fun getAttributesOrThrowIfUnavailable(file: VirtualFile): FileAttributes? =
    if (isInUnavailable(file)) throw unavailableException(file.parent) else super.getAttributes(file)

  override fun listWithAttributes(dir: VirtualFile, childrenNames: Set<String>?): Map<String, FileAttributes> =
    if (unavailable(dir)) emptyMap() else listing(dir, childrenNames)

  override fun listWithAttributesOrThrowIfUnavailable(dir: VirtualFile, names: Set<String>?): Map<String, FileAttributes> =
    if (unavailable(dir)) throw unavailableException(dir) else listing(dir, names)

  private fun isInUnavailable(file: VirtualFile): Boolean = file.parent?.let(unavailable) == true

  private fun unavailableException(dir: VirtualFile): FileSystemUnavailableException {
    beforeThrow(dir)
    return FileSystemUnavailableException(IOException("${dir.path} is unavailable"))
  }

  /** Bypasses [unavailable]: the listing of a parent reports an unavailable child directory as present. */
  private fun listing(dir: VirtualFile, names: Set<String>?): Map<String, FileAttributes> =
    list(dir).filter { names == null || it in names }.associateWith { checkNotNull(super.getAttributes(FakeVirtualFile(dir, it))) }
}
