// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch

import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import java.io.File

class PatchTextDetectionTest : PlatformTestCase() {
  fun testClassicalContextDiff() {
    doTest(true)
  }

  fun testClassicalUnifiedDiff() {
    doTest(true)
  }

  fun testContextDiffWithExtraInfo() {
    doTest(true)
  }

  fun testIdeaPatch() {
    doTest(true)
  }

  fun testRandomText() {
    doTest(false)
  }

  fun testNormalDiff() {
    doTest(false)
  }

  private fun doTest(expected: Boolean) {
    val testDataPath = PlatformTestUtil.getPlatformTestDataPath() + "diff/patchTextDetection/" + getTestName(true)
    createTestProjectStructure(testDataPath)
    val patchPath = "$testDataPath/test.patch"
    val patchFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(patchPath.replace(File.separatorChar, '/'))

    val patchContents = patchFile!!.contentsToByteArray()
    val patchText = LoadTextUtil.getTextByBinaryPresentation(patchContents, patchFile);
    assertEquals(expected, PatchReader.isPatchContent((patchText.toString())));
  }
}