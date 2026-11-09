// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl
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

  @Test
  fun `a failed listing keeps the children that exist and deletes the absent one`(): Unit = runBlocking {
    val a = checkNotNull(dir.findChild("a.txt"))
    val b = writeAction { dir.createChildData(this, "b.txt") }
    dir.children  // loads all the children, so the refresh does the full scan
    check(dir.allChildrenLoaded()) { "the test is about the full scan" }
    fs.deleteFile(this, b)
    fs.listingFails = { it.path == dir.path }

    refresh(dir)

    assertThat(a.isValid).describedAs("the stat by name confirms that a.txt exists").isTrue()
    assertThat(dir.findChild("a.txt")).isEqualTo(a)
    assertThat(b.isValid).describedAs("the stat by name confirms that b.txt is absent").isFalse()
    assertThat(dir.isDirty).describedAs("the next refresh must try the listing again").isTrue()
  }

  @Test
  fun `a failed listing in a partial scan keeps the cached child that exists and deletes the absent one`() {
    val root = checkNotNull(ManagingFS.getInstance().findRoot("/", fs))
    // the file system creates the files without VFS events, so the VFS discovers them lazily
    fs.createChildDirectory(this, root, "p").also { fs.createIfNotExists(it, "x.txt"); fs.createIfNotExists(it, "y.txt") }
    val p = checkNotNull(root.findChild("p"))
    val x = checkNotNull(p.findChild("x.txt"))
    val y = checkNotNull(p.findChild("y.txt"))
    check(!p.allChildrenLoaded()) { "the test is about the partial scan" }
    fs.deleteFile(this, y)
    fs.listingFails = { it.path == p.path }

    refresh(p)

    assertThat(x.isValid).describedAs("the stat by name confirms that x.txt exists").isTrue()
    assertThat(p.findChild("x.txt")).isEqualTo(x)
    assertThat(y.isValid).describedAs("the stat by name confirms that y.txt is absent").isFalse()
    assertThat(p.isDirty).describedAs("the next refresh must try the listing again").isTrue()
  }

  @Test
  fun `a successful listing is complete, so the refresh deletes the child that the listing lacks, with no stat`() {
    val a = checkNotNull(dir.findChild("a.txt"))
    fs.unlisted = { it.path == a.path }
    // a stat by name would make the state of a.txt unknown, and the refresh would keep it
    fs.statFails = { it.path == a.path }

    refresh(dir)

    assertThat(a.isValid).describedAs("the refresh trusts the listing, and does not stat a.txt by name").isFalse()
    assertThat(dir.isDirty).describedAs("the refresh knows the state of each child").isFalse()
  }

  @Test
  fun `the children stay while the listing and the stat fail, and the changes apply when the access returns`(): Unit = runBlocking {
    val a = checkNotNull(dir.findChild("a.txt"))
    // as a directory that macOS TCC protects: the listing and the stat of each child fail
    fs.listingFails = { it.path == dir.path }
    fs.statFails = { it.parent?.path == dir.path }

    refresh(dir)

    assertThat(a.isValid).isTrue()
    assertThat(dir.findChild("a.txt")).isEqualTo(a)
    assertThat(dir.isDirty).isTrue()

    fs.createIfNotExists(dir, "c.txt")
    fs.deleteFile(this, a)
    fs.listingFails = { false }
    fs.statFails = { false }
    refresh(dir)

    assertThat(dir.children.map { it.name }).containsExactly("c.txt")
    assertThat(a.isValid).isFalse()
  }

  @Test
  fun `a child whose stat fails stays, and so do the other children`(): Unit = runBlocking {
    val a = checkNotNull(dir.findChild("a.txt"))
    val b = writeAction { dir.createChildData(this, "b.txt") }
    // the listing meets a.txt and fails too, so the refresh checks each child by name
    fs.statFails = { it.path == a.path }

    refresh(dir)

    assertThat(a.isValid).describedAs("the state of a.txt is unknown, not absent").isTrue()
    assertThat(b.isValid).describedAs("the stat by name confirms that b.txt exists").isTrue()
    assertThat(dir.isDirty).describedAs("the state of a.txt is still unknown").isTrue()
  }

  @Test
  fun `a refresh root whose stat fails stays`() {
    fs.statFails = { it.path == dir.path }

    refresh(dir)

    assertThat(dir.isValid).describedAs("the state of the root is unknown, not absent").isTrue()
    assertThat(dir.findChild("a.txt")).isNotNull()
    assertThat(dir.isDirty).isTrue()
  }

  @Test
  fun `getChildren of a directory that cannot be listed does not mark it fully loaded, so a child is still found by name`() {
    fs.createChildDirectory(this, dir, "sub")
    fs.createChildFile(this, FakeVirtualFile(dir, "sub"), "x.txt")
    refresh(dir)
    val sub = checkNotNull(dir.findChild("sub")) as VirtualDirectoryImpl
    assertThat(sub.allChildrenCached()).describedAs("precondition: VFS has not listed sub yet").isFalse()
    fs.listingFails = { it.path == sub.path }

    assertThat(sub.children).describedAs("VFS knows no child of sub, and the listing fails").isEmpty()

    assertThat(sub.allChildrenLoaded()).describedAs("a failed listing is not an empty one").isFalse()
    assertThat(sub.allChildrenCached()).describedAs("a failed listing is not an empty one").isFalse()
    assertThat(sub.findChild("x.txt")).describedAs("sub is not fully loaded, so a lookup asks the file system by name").isNotNull()

    fs.listingFails = { false }
    assertThat(sub.children.map { it.name }).describedAs("the listing works again").containsExactly("x.txt")
    assertThat(sub.allChildrenLoaded()).isTrue()
    assertThat(sub.allChildrenCached()).isTrue()
  }

  private fun refresh(file: VirtualFile) {
    RefreshQueue.getInstance().createSession(false, true, null).apply { addFile(file) }.launch()
  }
}

/**
 * [unavailable] selects the directories whose content cannot be read: the stat of a child and the listing fail.
 * [listingFails], [unlisted] and [statFails] fail one read, as a single directory or file of `LocalFileSystemImpl` can.
 * The [BatchingFileSystem] reads throw. The public [getAttributes] answers "absent", as `LocalFileSystemImpl` does.
 */
private class UnavailableTempFileSystem : TempFileSystem(), BatchingFileSystem {
  private val protocol = "unavailable-${System.identityHashCode(this)}"

  @Volatile
  var unavailable: (VirtualFile) -> Boolean = { false }

  /** Runs right before a [BatchingFileSystem] read throws, with the unavailable directory. */
  var beforeThrow: (VirtualFile) -> Unit = {}

  /** The listing of these directories fails, as the listing of a directory without `r` does (POSIX `0711`) */
  @Volatile
  var listingFails: (VirtualFile) -> Boolean = { false }

  /** A listing lacks these files with no error, though they exist */
  @Volatile
  var unlisted: (VirtualFile) -> Boolean = { false }

  /** The stat of these files fails. A listing that meets such a file fails too, as the listing of `LocalFileSystemImpl` does */
  @Volatile
  var statFails: (VirtualFile) -> Boolean = { false }

  override fun getProtocol(): String = protocol

  override fun getAttributes(file: VirtualFile): FileAttributes? =
    if (isInUnavailable(file) || statFails(file)) null else super.getAttributes(file)

  override fun getAttributesOrThrow(file: VirtualFile): FileAttributes? = when {
    isInUnavailable(file) -> throw unavailableException(file.parent)
    statFails(file) -> throw FileStateUnknownException(IOException("cannot stat ${file.path}"))
    else -> super.getAttributes(file)
  }

  override fun listWithAttributes(dir: VirtualFile, childrenNames: Set<String>?): Map<String, FileAttributes> = when {
    unavailable(dir) -> throw unavailableException(dir)
    listingFails(dir) -> throw FileStateUnknownException(IOException("cannot list ${dir.path}"))
    else -> listing(dir, childrenNames)
  }

  private fun isInUnavailable(file: VirtualFile): Boolean = file.parent?.let(unavailable) == true

  private fun unavailableException(dir: VirtualFile): FileSystemUnavailableException {
    beforeThrow(dir)
    return FileSystemUnavailableException(IOException("${dir.path} is unavailable"))
  }

  /** Bypasses [unavailable]: the listing of a parent reports an unavailable child directory as present. */
  private fun listing(dir: VirtualFile, names: Set<String>?): Map<String, FileAttributes> =
    list(dir).filter { names == null || it in names }
      .map { FakeVirtualFile(dir, it) }
      .filterNot(unlisted)
      .onEach { if (statFails(it)) throw FileStateUnknownException(IOException("cannot stat ${it.path} in the listing")) }
      .associate { it.name to checkNotNull(super.getAttributes(it)) }
}
