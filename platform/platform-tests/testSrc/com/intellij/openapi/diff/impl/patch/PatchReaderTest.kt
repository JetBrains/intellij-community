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
import junit.framework.TestCase
import java.io.File

class PatchReaderTest : PlatformTestCase() {

  @Throws(Exception::class)
  fun testPatchWithFileModeChangedOnly() {
    TestCase.assertEquals(1, readPatches().size);
  }

  @Throws(Exception::class)
  fun testPatchWithFileModeAndOther() {
    TestCase.assertEquals(2, readPatches().size);
  }

  private fun readPatches(): List<FilePatch> {
    val testDataPath = PlatformTestUtil.getPlatformTestDataPath() + "diff/patchReader/" + getTestName(true)
    PsiTestUtil.createTestProjectStructure(myProject, myModule, testDataPath, PlatformTestCase.myFilesToDelete)
    val patchPath = testDataPath + "/test.patch"
    val patchFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(patchPath.replace(File.separatorChar, '/'))

    val patchContents = patchFile!!.contentsToByteArray()
    val patchText = LoadTextUtil.getTextByBinaryPresentation(patchContents, patchFile);
    val patchReader = PatchReader(patchText.toString())
    patchReader.parseAllPatches();
    return patchReader.allPatches;
  }
}