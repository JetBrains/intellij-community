// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.openapi.util.NotNullComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.util.ThrowableRunnable
import com.intellij.util.io.div
import com.intellij.util.io.write
import org.junit.Assert.assertArrayEquals
import java.util.concurrent.atomic.AtomicBoolean

class FileContentImplTest : HeavyPlatformTestCase() {
  fun `test text file`() {
    val text = "aaa"
    val content = createFileContent(text.toByteArray())
    assertArrayEquals(text.toByteArray(), content.content)
    assertEquals(text, content.contentAsText.toString())
  }

  fun `test binary file`() {
    val bytes = byteArrayOf(3, 5, 7, 11)
    val content = createFileContent(bytes, binary = true)
    assertArrayEquals(bytes, content.content)
    assertThrows(UnsupportedOperationException::class.java, ThrowableRunnable { content.contentAsText })
  }

  fun `test conversion of line separators for text file`() {
    val text = "a\r\nb"
    val content = createFileContent(text.toByteArray())
    assertEquals("a\nb", content.contentAsText.toString())
    assertArrayEquals("a\nb".toByteArray(), content.content)
  }

  fun `test no conversion of line separators for text file with slash n`() {
    val text = "a\nb"
    val content = createFileContent(text.toByteArray())
    assertEquals("a\nb", content.contentAsText.toString())
    assertArrayEquals("a\nb".toByteArray(), content.content)
  }

  fun `test no conversion of line separators for binary file`() {
    val bytes = "a\r\nb".toByteArray()
    val content = createFileContent(bytes, binary = true)
    assertArrayEquals(bytes, content.content)
  }

  fun `test file content created by text`() {
    val text = "a\r\nb"
    val textBytes = text.toByteArray()
    val virtualFile = createInputFile(textBytes)
    val content = FileContentImpl.createByText(virtualFile, text)
    assertEquals(text, content.contentAsText)
    assertArrayEquals(textBytes, content.content)
  }

  fun `test content computation is called only once`() {
    val text = "abc"
    val textBytes = text.toByteArray()
    val virtualFile = createInputFile(textBytes)
    val wasComputed = AtomicBoolean()
    val contentComputation = NotNullComputable<ByteArray> {
      check(wasComputed.compareAndSet(false, true))
      textBytes
    }
    val content = FileContentImpl.createByContent(virtualFile, contentComputation)
    assertArrayEquals(textBytes, content.content)
    assertEquals(text, content.contentAsText.toString())
    assertArrayEquals(textBytes, content.content)
    assertEquals(text, content.contentAsText.toString())
  }

  private fun createFileContent(bytes: ByteArray, binary: Boolean = false): FileContent {
    val virtualFile = createInputFile(bytes, binary)
    return FileContentImpl.createByFile(virtualFile, myProject)
  }

  private fun createInputFile(content: ByteArray, binary: Boolean = false): VirtualFile {
    val home = createTempDir("hashing-test").toPath()

    val path = if (binary) home / "file.jpg" else home / "test-file.txt"

    //we create the file via low-level code to make sure IntelliJ Utils would not alter the newlines in it
    path.write(content)

    val virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path) ?: error("Failed to find temp file")
    assertEquals(binary, virtualFile.fileType.isBinary)
    return virtualFile
  }

}
