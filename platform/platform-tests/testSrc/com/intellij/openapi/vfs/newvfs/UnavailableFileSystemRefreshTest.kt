// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

/** A refresh skips a file whose file system is unavailable instead of deleting it, and the file stays dirty. */
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

    fs.unavailable = { false }
    refresh(dir)

    assertThat(dir.children.map { it.name }).containsExactlyInAnyOrder("a.txt", "b.txt")
    // The new child marks the directory dirty again, so the clean old child shows the scan.
    assertThat(file.isDirty).isFalse()
  }

  @Test
  fun `a failed subdirectory keeps its chain dirty and the sibling clean`(): Unit = runBlocking {
    val (sub, sibling) = writeAction {
      listOf("sub", "sibling").map { name ->
        dir.createChildDirectory(this, name).also { it.createChildData(this, "x.txt") } as NewVirtualFile
      }
    }
    fs.unavailable = { it.path.startsWith(sub.path + "/") }

    refresh(dir)

    assertThat(sub.findChild("x.txt")).isNotNull()
    assertThat(dir.isDirty).isTrue()
    assertThat(sub.isDirty).isTrue()
    assertThat(sibling.isDirty).isFalse()
  }

  private fun refresh(file: VirtualFile) {
    RefreshQueue.getInstance().createSession(false, true, null).apply { addFile(file) }.launch()
  }
}

/** The strict reads fail for the files that [unavailable] selects; the lenient read answers "absent", as `LocalFileSystemImpl` does. */
private class UnavailableTempFileSystem : TempFileSystem(), UnavailabilityAwareFileSystem {
  private val protocol = "unavailable-${System.identityHashCode(this)}"

  var unavailable: (VirtualFile) -> Boolean = { false }

  override fun getProtocol(): String = protocol

  override fun getAttributes(file: VirtualFile): FileAttributes? = if (unavailable(file)) null else super.getAttributes(file)

  override fun getAttributesOrThrowIfUnavailable(file: VirtualFile): FileAttributes? =
    if (unavailable(file)) throw FileSystemUnavailableException(IOException("${file.path} is unavailable")) else super.getAttributes(file)

  /** Never called: [TempFileSystem] is not a `BatchingFileSystem`. */
  override fun listWithAttributesOrThrowIfUnavailable(dir: VirtualFile, names: Set<String>?): Map<String, FileAttributes> =
    throw UnsupportedOperationException()
}
