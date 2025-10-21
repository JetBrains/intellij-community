// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch

import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.*
import com.intellij.vcs.log.impl.VcsUserImpl
import java.io.File

@TestDataPath("\$CONTENT_ROOT/testData/diff/patchReader/")
class PatchReaderTest : HeavyPlatformTestCase() {
  private val author = VcsUserImpl("D D", "aaaa@gmail.com")
  private val doubleSurname = VcsUserImpl("D D-D", "aaaa@gmail.com")
  private val longName = VcsUserImpl("very long author-surname", "aaaa@gmail.com")
  private val baseRevision = "d48bebc211cc216aaa78bdf25d7f0b0143d6333b"
  private val subjectLine = "Subject line"

  @Throws(Exception::class)
  fun testPatchWithFileModeChangedOnly() {
    doTestPatchCount(1)
  }

  @Throws(Exception::class)
  fun testPatchWithFileModeAndOther() {
    doTestPatchCount(2)
  }

  @Throws(Exception::class)
  fun testPatchHeader() {
    doTest(PatchFileHeaderInfo(subjectLine, author, baseRevision))
  }

  @Throws(Exception::class)
  fun testPatchHeaderWithMultiLineMessage() {
    doTest(PatchFileHeaderInfo("$subjectLine\n\n* another line", author, baseRevision))
  }

  @Throws(Exception::class)
  fun testPatchHeaderMessageWithNoPatchPart() {
    doTest(PatchFileHeaderInfo(subjectLine, author, baseRevision))
  }

  @Throws(Exception::class)
  fun testPatchHeaderWithoutBase() {
    doTest(PatchFileHeaderInfo(subjectLine, author, null))
  }

  @Throws(Exception::class)
  fun testPatchHeaderWithoutAuthor() {
    doTest(PatchFileHeaderInfo(subjectLine, null, baseRevision))
  }

  @Throws(Exception::class)
  fun testPatchHeaderWithoutMessage() {
    doTest(PatchFileHeaderInfo("", author, baseRevision))
  }

  @Throws(Exception::class)
  fun testPatchHeaderWithoutAdditionalInfo() {
    doTest(PatchFileHeaderInfo("", null, null))
  }

  @Throws(Exception::class)
  fun testPatchHeaderAdditionalInfoInWrongPlace() {
    doTest(PatchFileHeaderInfo("", null, null))
  }

  @Throws(Exception::class)
  fun testPatchHeaderWithNoStateInfo() {
    doTest(PatchFileHeaderInfo(subjectLine, author, baseRevision))
  }

  @Throws(Exception::class)
  fun testPatchHeaderWithDoubleSurname() {
    doTest(PatchFileHeaderInfo(subjectLine, doubleSurname, baseRevision))
  }

  @Throws(Exception::class)
  fun testPatchHeaderWithLongName() {
    doTest(PatchFileHeaderInfo(subjectLine, longName, baseRevision))
  }

  @Throws(Exception::class)
  fun testNotAPatch() {
    assertThrows(PatchSyntaxException::class.java) {
      read()
    }
  }

  @Throws(Exception::class)
  fun testIdeaPatchWithRevision() {
    val actual = read()
    val patch = actual.allPatches.single()

    assertEquals("(revision $baseRevision)", patch.beforeVersionId)
    assertEquals("(date 1718781070896)", patch.afterVersionId)
    assertEquals(PatchFileHeaderInfo(subjectLine, null, null), actual.patchFileInfo)
  }

  @Throws(Exception::class)
  fun testPatchFromGitDiff() {
    val actual = read()
    val patch = actual.allPatches.single()

    assertEquals("2904247b52d1f", patch.beforeVersionId)
    assertEquals("817709f1a6fb1", patch.afterVersionId)
    assertEquals(PatchFileHeaderInfo("", null, null), actual.patchFileInfo)
  }

  @Throws(Exception::class)
  fun testOriginalLineNumbers() {
    val actual = read()
    val patch = actual.allPatches.single() as TextFilePatch
    val hunk = patch.hunks.single()
    val lines = hunk.lines

    // Verify that PatchLine objects have original line numbers from the diff file
    assertTrue("Should have at least one line", lines.isNotEmpty())
    
    // Check that line numbers are properly set (not all -1)
    val hasValidLineNumbers = lines.any { it.patchFileLineNumber >= 0 }
    assertTrue("At least some lines should have valid original line numbers", hasValidLineNumbers)
    
    // Verify specific line numbers based on the test patch structure
    assertEquals("Expected 7 lines in the patch", 7, lines.size)
    
    // Assert specific original line numbers for each line based on actual debug output:
    // Line 0: CONTEXT, 'first line', originalLineNumber=3
    assertEquals("First line original line number", 3, lines[0].patchFileLineNumber)
    assertEquals("First line should be context", PatchLine.Type.CONTEXT, lines[0].type)
    assertEquals("First line text", "first line", lines[0].text)
    
    // Line 1: REMOVE, 'second line', originalLineNumber=4
    assertEquals("Second line original line number", 4, lines[1].patchFileLineNumber)
    assertEquals("Second line should be deleted", PatchLine.Type.REMOVE, lines[1].type)
    assertEquals("Second line text", "second line", lines[1].text)
    
    // Line 2: ADD, 'second line modified', originalLineNumber=5
    assertEquals("Third line original line number", 5, lines[2].patchFileLineNumber)
    assertEquals("Third line should be added", PatchLine.Type.ADD, lines[2].type)
    assertEquals("Third line text", "second line modified", lines[2].text)
    
    // Line 3: ADD, 'new line added', originalLineNumber=6
    assertEquals("Fourth line original line number", 6, lines[3].patchFileLineNumber)
    assertEquals("Fourth line should be added", PatchLine.Type.ADD, lines[3].type)
    assertEquals("Fourth line text", "new line added", lines[3].text)
    
    // Line 4: CONTEXT, 'third line', originalLineNumber=7
    assertEquals("Fifth line original line number", 7, lines[4].patchFileLineNumber)
    assertEquals("Fifth line should be context", PatchLine.Type.CONTEXT, lines[4].type)
    assertEquals("Fifth line text", "third line", lines[4].text)
    
    // Line 5: CONTEXT, 'fourth line', originalLineNumber=8
    assertEquals("Sixth line original line number", 8, lines[5].patchFileLineNumber)
    assertEquals("Sixth line should be context", PatchLine.Type.CONTEXT, lines[5].type)
    assertEquals("Sixth line text", "fourth line", lines[5].text)
    
    // Line 6: CONTEXT, 'fifth line', originalLineNumber=9
    assertEquals("Seventh line original line number", 9, lines[6].patchFileLineNumber)
    assertEquals("Seventh line should be context", PatchLine.Type.CONTEXT, lines[6].type)
    assertEquals("Seventh line text", "fifth line", lines[6].text)
  }

  private fun doTestPatchCount(expected: Int) {
    val actual = read().allPatches.size
    assertEquals(expected, actual)
  }

  private fun doTest(expected: PatchFileHeaderInfo) {
    val actual = read().patchFileInfo
    assertEquals(expected, actual)
  }

  private fun read(): PatchReader {
    val testDataPath = getTestDir(getTestName(true))
    createTestProjectStructure(testDataPath)
    val patchPath = "$testDataPath/test.patch"
    val patchFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(patchPath.replace(File.separatorChar, '/'))

    val patchContents = patchFile!!.contentsToByteArray()
    val patchText = LoadTextUtil.getTextByBinaryPresentation(patchContents, patchFile)
    val patchReader = PatchReader(patchText.toString())
    patchReader.parseAllPatches()
    return patchReader
  }

  private fun getTestDir(@TestDataFile dirName: String): String {
    return PlatformTestUtil.getPlatformTestDataPath() + "diff/patchReader/" + dirName
  }
}