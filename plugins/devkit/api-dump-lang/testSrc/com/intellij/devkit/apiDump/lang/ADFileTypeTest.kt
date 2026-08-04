// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

// The api-dump file names used to be recognized by ADFileType.isMyFileType();
// they are declared in intellij.devkit.apiDump.lang.xml now, so keep them covered.
internal class ADFileTypeTest : BasePlatformTestCase() {

  fun testApiDumpFileNamesAreRecognized() {
    assertFileType("api-dump.txt")
    assertFileType("api-dump-unreviewed.txt")
    assertFileType("api-dump-experimental.txt")
  }

  // temp files produced by ApiCheckTest
  fun testActualApiDumpFileNamesAreRecognized() {
    assertFileType("actual_api-dump.txt")
    assertFileType("actual_api-dump42.txt")
    assertFileType("actual_api-dump-unreviewed.txt")
    assertFileType("actual_api-dump-experimental42.txt")
  }

  fun testUnrelatedTextFilesAreNotRecognized() {
    assertNotFileType("readme.txt")
    assertNotFileType("api-dump.md")
  }

  private fun assertFileType(fileName: String) {
    assertEquals(fileName, ADFileType, FileTypeManager.getInstance().getFileTypeByFileName(fileName))
  }

  private fun assertNotFileType(fileName: String) {
    assertFalse(fileName, ADFileType == FileTypeManager.getInstance().getFileTypeByFileName(fileName))
  }
}
