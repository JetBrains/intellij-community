// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch

import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.vcs.log.impl.VcsUserImpl
import junit.framework.TestCase
import java.io.File

class PatchReaderTest : PlatformTestCase() {
  private val author = VcsUserImpl("D D", "aaaa@gmail.com")
  private val doubleSurname = VcsUserImpl("D D-D", "aaaa@gmail.com")
  private val longName = VcsUserImpl("very long author-surname", "aaaa@gmail.com")
  private val baseRevision = "d48bebc211cc216aaa78bdf25d7f0b0143d6333b"
  private val subjectLine = "Subject line"

  @Throws(Exception::class)
  fun testPatchWithFileModeChangedOnly() {
    TestCase.assertEquals(1, read().allPatches.size)
  }

  @Throws(Exception::class)
  fun testPatchWithFileModeAndOther() {
    TestCase.assertEquals(2, read().allPatches.size)
  }

  @Throws(Exception::class)
  fun testPatchHeader() {
    TestCase.assertEquals(PatchFileHeaderInfo(subjectLine, author, baseRevision), read().patchFileInfo)
  }

  @Throws(Exception::class)
  fun testPatchHeaderWithMultiLineMessage() {
    TestCase.assertEquals(PatchFileHeaderInfo("$subjectLine\n\n* another line", author, baseRevision), read().patchFileInfo)
  }

  @Throws(Exception::class)
  fun testPatchHeaderMessageWithNoPatchPart() {
    TestCase.assertEquals(PatchFileHeaderInfo(subjectLine, author, baseRevision), read().patchFileInfo)
  }

  @Throws(Exception::class)
  fun testPatchHeaderWithoutBase() {
    TestCase.assertEquals(PatchFileHeaderInfo(subjectLine, author, null), read().patchFileInfo)
  }

  @Throws(Exception::class)
  fun testPatchHeaderWithoutAuthor() {
    TestCase.assertEquals(PatchFileHeaderInfo(subjectLine, null, baseRevision), read().patchFileInfo)
  }

  @Throws(Exception::class)
  fun testPatchHeaderWithoutMessage() {
    TestCase.assertEquals(PatchFileHeaderInfo("", author, baseRevision), read().patchFileInfo)
  }

  @Throws(Exception::class)
  fun testPatchHeaderWithoutAdditionalInfo() {
    TestCase.assertEquals(PatchFileHeaderInfo("", null, null), read().patchFileInfo)
  }

  @Throws(Exception::class)
  fun testPatchHeaderAdditionalInfoInWrongPlace() {
    TestCase.assertEquals(PatchFileHeaderInfo("", null, null), read().patchFileInfo)
  }

  @Throws(Exception::class)
  fun testPatchHeaderWithNoStateInfo() {
    TestCase.assertEquals(PatchFileHeaderInfo(subjectLine, author, baseRevision), read().patchFileInfo)
  }

  @Throws(Exception::class)
  fun testPatchHeaderWithDoubleSurname() {
    TestCase.assertEquals(PatchFileHeaderInfo(subjectLine, doubleSurname, baseRevision), read().patchFileInfo)
  }

  @Throws(Exception::class)
  fun testPatchHeaderWithLongName() {
    TestCase.assertEquals(PatchFileHeaderInfo(subjectLine, longName, baseRevision), read().patchFileInfo)
  }

  private fun read(): PatchReader {
    val testDataPath = PlatformTestUtil.getPlatformTestDataPath() + "diff/patchReader/" + getTestName(true)
    createTestProjectStructure(testDataPath)
    val patchPath = "$testDataPath/test.patch"
    val patchFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(patchPath.replace(File.separatorChar, '/'))

    val patchContents = patchFile!!.contentsToByteArray()
    val patchText = LoadTextUtil.getTextByBinaryPresentation(patchContents, patchFile)
    val patchReader = PatchReader(patchText.toString())
    patchReader.parseAllPatches()
    return patchReader
  }
}