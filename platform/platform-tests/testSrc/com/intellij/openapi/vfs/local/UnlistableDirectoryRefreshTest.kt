// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.local

import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.deleteExisting

/**
 * A refresh of the real [LocalFileSystem] keeps the children of a directory that it cannot list (IJPL-257147).
 * `0311` (`-wx`): the listing fails, the children are accessible by name, as in a directory without `r`.
 * `0000`: the listing and the stat of each child fail, as in a directory that macOS TCC protects.
 */
@TestApplication
class UnlistableDirectoryRefreshTest {
  @TempDir
  lateinit var tempDir: Path

  private lateinit var dirPath: Path
  private lateinit var dir: VirtualDirectoryImpl

  @BeforeEach
  fun createDirectory() {
    IoTestUtil.assumeUnix()
    dirPath = tempDir.resolve("dir").createDirectory()
    dirPath.resolve("a.txt").createFile()
    dirPath.resolve("b.txt").createFile()
    dir = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dirPath)) as VirtualDirectoryImpl
  }

  @Test
  fun `a full refresh of a directory without r keeps the child that exists and deletes the absent one`() {
    val (a, b) = dir.children.sortedBy { it.name }
    val aId = (a as VirtualFileWithId).id
    dirPath.resolve("b.txt").deleteExisting()

    withPermissions("-wx--x--x") {
      refresh(dir)

      assertThat(a.isValid).describedAs("the stat by name confirms that a.txt exists").isTrue()
      assertThat((dir.findChild("a.txt") as VirtualFileWithId).id).describedAs("a.txt keeps its id").isEqualTo(aId)
      assertThat(b.isValid).describedAs("the stat by name confirms that b.txt is absent").isFalse()
      assertThat(dir.isDirty).describedAs("the next refresh must try the listing again").isTrue()
    }

    dirPath.resolve("c.txt").createFile()
    refresh(dir)
    assertThat(dir.children.map { it.name }).describedAs("the listing works again").containsExactlyInAnyOrder("a.txt", "c.txt")
  }

  @Test
  fun `a partial refresh of a directory without r keeps the child that exists, and getChildren finds the others by name`() {
    val dir = refindAfterVfsLoadedNoChildren()
    val a = checkNotNull(dir.findChild("a.txt"))
    assertThat(dir.allChildrenLoaded()).describedAs("precondition: a partial refresh").isFalse()

    withPermissions("-wx--x--x") {
      refresh(dir)
      assertThat(a.isValid).describedAs("the stat by name confirms that a.txt exists").isTrue()

      assertThat(dir.children.map { it.name }).describedAs("VFS knows only a.txt, and the listing fails").containsExactly("a.txt")
      assertThat(dir.allChildrenLoaded()).describedAs("a failed listing is not a complete one").isFalse()
      assertThat(dir.findChild("b.txt")).describedAs("a lookup still asks the file system by name").isNotNull()
    }
  }

  @Test
  fun `the children of a directory without any access stay, and the changes apply when the access returns`() {
    val (a, b) = dir.children.sortedBy { it.name }

    withPermissions("---------") {
      refresh(dir)

      assertThat(a.isValid).describedAs("the state of a.txt is unknown, not absent").isTrue()
      assertThat(b.isValid).describedAs("the state of b.txt is unknown, not absent").isTrue()
      assertThat(dir.isDirty).isTrue()
    }

    dirPath.resolve("b.txt").deleteExisting()
    refresh(dir)
    assertThat(dir.children.map { it.name }).containsExactly("a.txt")
    assertThat(b.isValid).isFalse()
  }

  /** A directory record that VFS has not listed yet: a refresh of such a directory is partial */
  private fun refindAfterVfsLoadedNoChildren(): VirtualDirectoryImpl {
    val sub = dirPath.resolve("sub").createDirectory()
    sub.resolve("a.txt").createFile()
    sub.resolve("b.txt").createFile()
    dirPath = sub
    // `dir` is not fully loaded either, so the lookup asks the file system by name
    return checkNotNull(dir.findChild("sub")) as VirtualDirectoryImpl
  }

  /** Runs [action] while [dirPath] has [permissions], and restores them: else the temporary directory cannot be deleted */
  private fun withPermissions(permissions: String, action: () -> Unit) {
    val original = Files.getPosixFilePermissions(dirPath)
    Files.setPosixFilePermissions(dirPath, PosixFilePermissions.fromString(permissions))
    try {
      // root, for example, lists any directory
      assumeTrue(runCatching { Files.list(dirPath).close() }.isFailure, "the listing of $dirPath must fail")
      action()
    }
    finally {
      Files.setPosixFilePermissions(dirPath, original)
    }
  }

  private fun refresh(file: VirtualFile) {
    (file as NewVirtualFile).markDirtyRecursively()
    file.refresh(false, true)
  }
}