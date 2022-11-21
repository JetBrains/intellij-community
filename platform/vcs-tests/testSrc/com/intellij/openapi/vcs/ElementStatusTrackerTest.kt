// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.psi.PsiDocumentManager

class ElementStatusTrackerTest : BaseLineStatusTrackerTestCase() {
  fun testPsiChanges() {
    doTest("""
        <xml>
        </xml>""".trimIndent(), FileStatus.ADDED)
    doTest("""
        <xml>
          <tag>Hello World</tag>
        </xml>""".trimIndent(), FileStatus.MODIFIED)
    doTest("""
        <xml>
          <tag>Hello</tag>
          <tag1>Hello</tag1>
        </xml>""".trimIndent(), FileStatus.NOT_CHANGED)
  }

  private fun doTest(vcsText: String, expectedStatus: FileStatus) {
    val updatedText = """
        <xml>
          <tag>Hello</tag>
        </xml>""".trimIndent()
    test(updatedText, vcsText, true, "test.xml") {
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)!!
      val psiElement = psiFile.findElementAt(document.getLineStartOffset(1) + 3)!!.parent
      assertEquals("<tag>Hello</tag>", psiElement.text)
      assertEquals(expectedStatus, ElementStatusTracker.getInstance(project).getElementStatus(psiElement))
    }
  }
}