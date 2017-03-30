package com.intellij.openapi.diff.impl.patch

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.PsiTestUtil
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

    val testDataPath = PathManagerEx.getTestDataPath() + "/diff/patchTextDetection/" + getTestName(true)
    PsiTestUtil.createTestProjectStructure(myProject, myModule, testDataPath, PlatformTestCase.myFilesToDelete)
    val patchPath = testDataPath + "/test.patch"
    val patchFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(patchPath.replace(File.separatorChar, '/'))

    val patchContents = patchFile!!.contentsToByteArray()
    val patchText = LoadTextUtil.getTextByBinaryPresentation(patchContents, patchFile);
    assertEquals(expected, PatchReader.isPatchContent((patchText.toString())));
  }

}