// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.encoding.EncodingManager
import com.intellij.testFramework.TemporaryDirectory.Companion.generateTemporaryPath
import org.editorconfig.Utils
import org.editorconfig.configmanagement.EditorConfigEncodingCache.Companion.getInstance
import org.junit.Assert
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class EditorConfigEncodingTest : EditorConfigFileSettingsTestCase() {
  fun testUtf8Bom() {
    val newFile = createTargetFile()
    Assert.assertArrayEquals(CharsetToolkit.UTF8_BOM, newFile.bom)
  }

  fun testOverridden() {
    val newFile = createTargetFile()
    val charset = EncodingManager.getInstance().getEncoding(newFile, true)
    assertEquals(StandardCharsets.ISO_8859_1, charset)
  }

  fun testForcedUtf8() {
    val newFile = createTargetFile()
    val dir = newFile.parent
    val editorConfig = dir.findChild(Utils.EDITOR_CONFIG_FILE_NAME)
    assertNotNull(editorConfig)
    val charset = editorConfig!!.charset
    assertEquals(StandardCharsets.UTF_8, charset)
  }

  // IDEA-317486
  fun testSpaceAndCommentAfterCharset() {
    val newFile = createTargetFile()
    val charset = EncodingManager.getInstance().getEncoding(newFile, true)
    assertEquals(StandardCharsets.ISO_8859_1, charset)
  }

  @Throws(IOException::class)
  private fun createTargetFile(): VirtualFile {
    val dir = generateTemporaryPath(getTestName(true))
    Files.createDirectories(dir)
    Files.copy(testDataPath.resolve(".editorconfig"), dir.resolve(".editorconfig"))
    val targetDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir)
    val file = WriteAction.computeAndWait<VirtualFile, IOException> { targetDir!!.createChildData(this, "test.txt") }
    getInstance().computeAndCacheEncoding(project, file)
    return file
  }

  override fun getRelativePath(): String {
    return "plugins/editorconfig/testData/org/editorconfig/configmanagement/encoding"
  }
}