// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch

import com.intellij.diff.HeavyDiffTestCase
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.SimpleContentRevision
import com.intellij.vcsUtil.VcsUtil
import java.io.StringWriter

class CreateApplyPatchSymmetryTest : HeavyDiffTestCase() {
  val RUNS = 10000
  val TEXT_SIZE = 100

  fun testPatches() {
    doAutoTest(System.currentTimeMillis(), RUNS) { debugData ->
      val text1 = generateText(TEXT_SIZE)
      val text2 = generateText(TEXT_SIZE)
      debugData.put("Text1", text1)
      debugData.put("Text2", text2)

      if (text1 == text2) return@doAutoTest // No changes - no patch

      doTest(text1, text2, debugData)
    }
  }

  fun testWholeFileDeletion() {
    doTest("x", "")
    doTest("x\n", "")
    doTest("x\ny", "")
    doTest("x\ny\n", "")
  }

  fun testInsertionIntoEmptyFile() {
    doTest("", "x")
    doTest("", "x\n")
    doTest("", "x\ny")
    doTest("", "x\ny\n")
  }

  fun testLineEndings() {
    val unchanged = "1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11\n12\n13\n"

    doTest("x", "x\n")
    doTest("x", "x\ny")
    doTest("x", "x\ny\n")
    doTest("x\n", "x\ny")
    doTest("x\n", "x\ny\n")

    doTest("${unchanged}x", "${unchanged}x\n")
    doTest("${unchanged}x", "${unchanged}x\ny")
    doTest("${unchanged}x", "${unchanged}x\ny\n")
    doTest("${unchanged}x\n", "${unchanged}x\ny")
    doTest("${unchanged}x\n", "${unchanged}x\ny\n")

    doTest("x\n", "x")
    doTest("x\ny", "x")
    doTest("x\ny\n", "x")
    doTest("x\ny", "x\n")
    doTest("x\ny\n", "x\n")

    doTest("${unchanged}x\n", "${unchanged}x")
    doTest("${unchanged}x\ny", "${unchanged}x")
    doTest("${unchanged}x\ny\n", "${unchanged}x")
    doTest("${unchanged}x\ny", "${unchanged}x\n")
    doTest("${unchanged}x\ny\n", "${unchanged}x\n")
  }

  private fun doTest(text1: String, text2: String, debugData: DebugData? = null) {
    val basePath = "/base/"
    val path = VcsUtil.getFilePath("/base/some/file.txt", false)

    val change = Change(SimpleContentRevision(text1, path, "1"),
                        SimpleContentRevision(text2, path, "2"))
    val createdPatches = IdeaTextPatchBuilder.buildPatch(project, listOf(change), basePath, false)

    val writer = StringWriter()
    UnifiedDiffWriter.write(project, createdPatches, writer, "\n", null)
    val patchText = writer.toString()
    debugData?.put("Patch", patchText)


    val reader = PatchReader(patchText)
    val parsedPatches = reader.readTextPatches()

    val parsedPatch = parsedPatches.single()

    val text3 = GenericPatchApplier.apply(text1, parsedPatch.hunks)!!.patchedText
    debugData?.put("Text3", text3)

    assertEquals(text2, text3)
  }
}