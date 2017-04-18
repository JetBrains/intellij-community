/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.diff.impl.patch

import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
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
    val testDataPath = PlatformTestUtil.getPlatformTestDataPath() + "diff/patchTextDetection/" + getTestName(true)
    PsiTestUtil.createTestProjectStructure(myProject, myModule, testDataPath, PlatformTestCase.myFilesToDelete)
    val patchPath = testDataPath + "/test.patch"
    val patchFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(patchPath.replace(File.separatorChar, '/'))

    val patchContents = patchFile!!.contentsToByteArray()
    val patchText = LoadTextUtil.getTextByBinaryPresentation(patchContents, patchFile);
    assertEquals(expected, PatchReader.isPatchContent((patchText.toString())));
  }

}