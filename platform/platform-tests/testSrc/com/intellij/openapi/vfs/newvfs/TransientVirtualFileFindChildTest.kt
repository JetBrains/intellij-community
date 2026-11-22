// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** [TransientVirtualFileImpl.findChild] checks the one child on disk and adds nothing to the VFS. */
@TestApplication
class TransientVirtualFileFindChildTest {
  @Test
  fun `a transient directory finds a child on disk and no missing one`(@TempDir dir: Path) {
    val nested = Files.createDirectories(dir.resolve("a/b"))
    Files.writeString(nested.resolve("file.txt"), "text")
    val directory = LocalFileSystem.getInstance().findFileByPathWithoutCaching(nested.toString())
    assertTrue(directory is TransientVirtualFileImpl, "a directory the VFS does not cache is transient: $directory")

    val child = directory!!.findChild("file.txt")!!
    assertTrue(child is TransientVirtualFileImpl, "the child stays out of the VFS: $child")
    assertEquals("${directory.path}/file.txt", child.path)
    assertEquals(directory, child.parent)
    assertFalse(child.isDirectory)
    assertEquals("text", String(child.contentsToByteArray()))
    assertNull(directory.findChild("missing.txt"))
    assertNull(directory.findChild("file.txt/x"), "a name with a separator names no child")
  }
}
