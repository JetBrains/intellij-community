// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.ex.temp

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VFileProperty
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class TempFileSystemSymlinkTest {
  private val fs: TempFileSystem get() = TempFileSystem.getInstance()
  private lateinit var root: VirtualFile

  @BeforeEach
  fun setUp() {
    root = WriteAction.computeAndWait<VirtualFile, Exception> {
      fs.findFileByPath("/")!!.createChildDirectory(this, "symlinks")
    }
  }

  @AfterEach
  fun tearDown() {
    WriteAction.runAndWait<Exception> { root.delete(this) }
  }

  @Test
  fun `a link to a file reads the file`() {
    val target = createFile("dir/a.txt")
    val link = createLink(root, "link.txt", "/symlinks/dir/a.txt")

    assertThat(link.`is`(VFileProperty.SYMLINK)).isTrue()
    assertThat(link.isDirectory).isFalse()
    assertThat(VfsUtilCore.loadText(link)).isEqualTo("text")
    assertThat(link.canonicalPath).isEqualTo("/symlinks/dir/a.txt")
    assertThat(link.canonicalFile).isEqualTo(target)
  }

  @Test
  fun `a link to a directory lists the directory`() {
    val target = createFile("dir/a.txt")
    val link = createLink(root, "linkDir", "dir")

    assertThat(link.`is`(VFileProperty.SYMLINK)).isTrue()
    assertThat(link.isDirectory).isTrue()
    val child = link.findChild("a.txt")!!
    assertThat(VfsUtilCore.loadText(child)).isEqualTo("text")
    assertThat(child.path).isEqualTo("/symlinks/linkDir/a.txt")
    assertThat(child.canonicalPath).isEqualTo("/symlinks/dir/a.txt")
    assertThat(child.canonicalFile).isEqualTo(target)
  }

  @Test
  fun `a chain of links resolves to the last target`() {
    createFile("dir/a.txt")
    createLink(root, "first", "dir")
    val second = createLink(root, "second", "/symlinks/first")
    val link = createLink(root, "link.txt", "second/a.txt")

    assertThat(second.canonicalPath).isEqualTo("/symlinks/dir")
    assertThat(link.canonicalPath).isEqualTo("/symlinks/dir/a.txt")
    assertThat(VfsUtilCore.loadText(link)).isEqualTo("text")
  }

  @Test
  fun `a broken link and a loop of links have no target`() {
    val broken = createLink(root, "broken", "missing.txt")
    createLink(root, "loop1", "loop2")
    val loop = createLink(root, "loop2", "loop1")

    assertThat(broken.exists()).isTrue()
    assertThat(broken.`is`(VFileProperty.SYMLINK)).isTrue()
    assertThat(broken.canonicalPath).isNull()
    assertThat(loop.`is`(VFileProperty.SYMLINK)).isTrue()
    assertThat(loop.canonicalPath).isNull()
  }

  @Test
  fun `the delete of a link keeps its target`() {
    val target = createFile("a.txt")
    val link = createLink(root, "link.txt", "a.txt")

    WriteAction.runAndWait<Exception> { link.delete(this) }

    assertThat(link.isValid).isFalse()
    assertThat(target.isValid).isTrue()
    assertThat(VfsUtilCore.loadText(target)).isEqualTo("text")
  }

  /** Creates the file at [relativePath] with the text `text`. */
  private fun createFile(relativePath: String): VirtualFile =
    WriteAction.computeAndWait<VirtualFile, Exception> {
      val directory = VfsUtil.createDirectoryIfMissing(root, relativePath.substringBeforeLast('/', ""))
      directory.createChildData(this, relativePath.substringAfterLast('/')).also { VfsUtil.saveText(it, "text") }
    }

  private fun createLink(parent: VirtualFile, name: String, target: String): VirtualFile {
    fs.createSymbolicLink(parent, name, target)
    runInEdtAndWait { VfsUtil.markDirtyAndRefresh(false, false, true, parent) }
    return parent.findChild(name)!!
  }
}
