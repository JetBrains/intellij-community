// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.changes.patch.BinaryPatchWriter.writeBinaries
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestDataFile
import com.intellij.testFramework.TestDataPath
import java.io.StringWriter
import java.nio.file.Paths
import java.util.*

@TestDataPath("\$CONTENT_ROOT/testData/diff/binaryPatch/")
class BinaryPatchTest : HeavyPlatformTestCase() {
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
    val testDataPath = Paths.get(getTestDir(getTestName(true)))
    val dataFile = testDataPath.resolve(dataFileName).toFile()
    dataFile.setExecutable(false)
    val decodedContentBytes = FileUtil.loadFileBytes(dataFile)
    val encodedFile = testDataPath.resolve(filePatchName)
    val stringWriter = StringWriter()
    val binaryPatch = if (reverse) BinaryFilePatch(decodedContentBytes, null) else BinaryFilePatch(null, decodedContentBytes)
    binaryPatch.beforeName = dataFileName
    binaryPatch.afterName = dataFileName
    writeBinaries(testDataPath, listOf(binaryPatch), stringWriter)
    assertEquals(FileUtil.loadFile(encodedFile.toFile(), true), stringWriter.toString())
    val reader = PatchReader(encodedFile)
    reader.parseAllPatches()
    val patches = reader.allPatches
    assertTrue(patches.size == 1)
    assertTrue(Arrays.equals(binaryPatch.afterContent, (patches.first() as BinaryFilePatch).afterContent))
  }

  private fun getTestDir(@TestDataFile dirName: String): String {
    return PlatformTestUtil.getPlatformTestDataPath() + "diff/binaryPatch/" + dirName
  }
}