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

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.changes.patch.BinaryPatchWriter.writeBinaries
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataFile
import com.intellij.testFramework.TestDataPath
import java.io.File
import java.io.StringWriter
import java.util.*

@TestDataPath("\$CONTENT_ROOT/testData/diff/binaryPatch/")
class BinaryPatchTest : PlatformTestCase() {

  var dataFileName = "data.bin"
  var filePatchName = "file.patch"

  fun testAddedPng() {
    doTest()
  }

  fun testAddedEmptyPng() {
    doTest()
  }

  fun testAddedGif() {
    doTest()
  }

  fun testLen1() {
    doTest()

  }

  fun testLen2() {
    doTest()
  }

  fun testLen3() {
    doTest()
  }

  fun testLetterXasLen() {
    doTest()
  }

  fun testReversePatchCreation() {
    doTest(true)
  }

  private fun doTest(reverse: Boolean = false) {
    val testDataPath = getTestDir(getTestName(true))
    val dataFile = File(testDataPath, dataFileName)
    dataFile.setExecutable(false)
    val decodedContentBytes = FileUtil.loadFileBytes(dataFile)
    val encodedFile = File(testDataPath, filePatchName)
    val stringWriter = StringWriter()
    val binaryPatch = if (reverse) BinaryFilePatch(decodedContentBytes, null) else BinaryFilePatch(null, decodedContentBytes)
    binaryPatch.beforeName = dataFileName
    binaryPatch.afterName = dataFileName
    writeBinaries(testDataPath, listOf(binaryPatch), stringWriter)
    assertEquals(FileUtil.loadFile(encodedFile, true), stringWriter.toString())
    val reader = PatchVirtualFileReader.create(VfsUtil.findFileByIoFile(encodedFile, true))
    reader.parseAllPatches()
    val patches = reader.allPatches
    assertTrue(patches.size == 1)
    assertTrue(Arrays.equals(binaryPatch.afterContent, (patches.first() as BinaryFilePatch).afterContent))
  }

  private fun getTestDir(@TestDataFile dirName: String): String {
    return PlatformTestUtil.getPlatformTestDataPath() + "diff/binaryPatch/" + dirName
  }
}