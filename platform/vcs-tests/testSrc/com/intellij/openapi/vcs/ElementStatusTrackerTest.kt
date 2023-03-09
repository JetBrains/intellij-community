// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.xml.XmlFile

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
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) as XmlFile
      val firstSubTag = psiFile.rootTag!!.subTags[0]
      assertEquals("<tag>Hello</tag>", firstSubTag.text)
      assertEquals(expectedStatus, ElementStatusTracker.getInstance(project).getElementStatus(firstSubTag))
    }
  }
}