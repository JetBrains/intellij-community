// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.util.NotNullComputable
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.util.ArrayUtil
import com.intellij.util.io.write
import junit.framework.TestCase
import org.junit.Assert.assertArrayEquals
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class FileContentImplTest : HeavyPlatformTestCase() {

  private val CHARSETS_WITH_BOM = listOf(
    StandardCharsets.UTF_8,
    StandardCharsets.UTF_16LE,
    StandardCharsets.UTF_16BE,
    CharsetToolkit.UTF_32LE_CHARSET,
    CharsetToolkit.UTF_32BE_CHARSET
  )

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
    assertThrows(UnsupportedOperationException::class.java, { content.contentAsText })
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
    val content = FileContentImpl.createByText(virtualFile, text, project)
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

  fun `test bom is truncated from getContent()`() {
    val text = "哇你居然翻译了这篇中文文本"
    for (charset in CHARSETS_WITH_BOM) {
      val bom = CharsetToolkit.getPossibleBom(charset)!!
      val bytes = ArrayUtil.mergeArrays(bom, text.toByteArray (charset))
      val fileContent = createFileContent(bytes)
      assertFalse(fileContent.fileType.isBinary)
      val detectedBom = LoadTextUtil.guessFromContent(fileContent.file, fileContent.content).BOM
      assertNull(detectedBom)
    }
  }

  fun `test contentAsText() returns correct content for files with BOM`() {
    val text = "<xml/>"
    for (charset in CHARSETS_WITH_BOM) {
      for (ext in listOf("xml", "txt")) {
        // in txt files content {60,0,120,0,...}=("<x..." in UTF16) is recognized as "guessed" binary content
        // in xml files content {60,0,120,0,...}=("<x..." in UTF16) is recognized as "hardCoded" UTF8 content (see XmlLikeFileType.getCharset which defaults to UTF8)
        val bom = CharsetToolkit.getPossibleBom(charset)!!
        val bytes = ArrayUtil.mergeArrays(bom, text.toByteArray(charset))
        val fileContent = createFileContent(bytes, ext)
        val contentAsText = fileContent.contentAsText
        val detectedFileType = FileTypeManagerEx.getInstance().getFileTypeByFile(fileContent.file)
        TestCase.assertFalse(detectedFileType.isBinary)
        TestCase.assertEquals("Wrong text: charset=$charset, file=$ext", text, contentAsText.toString())
      }
    }

  }

  private fun createFileContent(bytes: ByteArray, binary: Boolean = false): FileContent {
    return createFileContent(bytes, suggestExtension(binary))
  }

  private fun createFileContent(bytes: ByteArray, fileExtension: String): FileContent {
    val virtualFile = createInputFile(bytes, fileExtension)
    return FileContentImpl.createByFile(virtualFile, myProject)
  }

  private fun createInputFile(content: ByteArray, binary: Boolean = false): VirtualFile {
    return createInputFile(content, suggestExtension(binary)).also {
      assertEquals(binary, it.fileType.isBinary)
    }
  }

  private fun suggestExtension(binary: Boolean) = if (binary) "jpg" else "txt"

  private fun createInputFile(content: ByteArray, fileExtension: String): VirtualFile {
    val home = createTempDir("hashing-test").toPath()

    val path = home.resolve("test-file.$fileExtension")

    //we create the file via low-level code to make sure IntelliJ Utils would not alter the newlines in it
    path.write(content)

    return VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path) ?: error("Failed to find temp file")
  }

}
