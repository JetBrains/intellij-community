package com.intellij.openapi.diff.impl.patch

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.changes.patch.BinaryPatchWriter.writeBinaries
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.IdeaTestCase
import java.io.File
import java.io.StringWriter
import java.util.*

class BinaryPatchTest : IdeaTestCase() {

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
    val testDataPath = "${PathManagerEx.getTestDataPath()}/diff/binaryPatch/${getTestName(true)}"
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
}