// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.psi.PsiDocumentManager

class ElementStatusTrackerTest : BaseLineStatusTrackerTestCase() {
  fun testPsiChanges() {
    doTest("", FileStatus.ADDED)
    doTest("OldContent", FileStatus.MODIFIED)
    doTest("Content\n", FileStatus.NOT_CHANGED)
  }

  private fun doTest(vcsText: String, expectedStatus: FileStatus) {
    test("Content\n", vcsText, true) {
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)!!
      assertEquals(expectedStatus, ElementStatusTracker.getInstance(project).getElementStatus(psiFile))
    }
  }
}