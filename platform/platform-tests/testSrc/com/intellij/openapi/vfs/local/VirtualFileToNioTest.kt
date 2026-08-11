// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.local

import com.intellij.mock.MockLocalFileSystem
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestApplication
internal class VirtualFileToNioTest {
  @Test
  fun testNioPathOk(@TempDir path: Path): Unit = timeoutRunBlocking {
    val vFile = writeAction {
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
    }
    Assertions.assertEquals(path.toRealPath(), vFile.toNioPathOrNull()?.toRealPath())
  }

   @Test
  fun testNioPathUnsupported() {
    val vFile = StubVirtualFile(object : MockLocalFileSystem() {
      override fun getNioPath(file: VirtualFile): Path? {
        throw UnsupportedOperationException("This FS isn't nio based")
      }
    })
    Assertions.assertNull(vFile.toNioPathOrNull())
  }

  @Test
  fun testNioPathBroken() {
    val vFile = StubVirtualFile(object : MockLocalFileSystem() {
      override fun getNioPath(file: VirtualFile): Path? {
        throw NullPointerException("Bug")
      }
    })
    Assertions.assertThrows(NullPointerException::class.java) {
      vFile.toNioPathOrNull()
    }
  }
}
