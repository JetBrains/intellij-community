// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.testAssistant

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import junit.framework.TestCase
import org.jetbrains.idea.devkit.testAssistant.TestDataPathTestCase

class KtDataPathReferenceTest : TestDataPathTestCase() {

  fun testRootAndSubdir() {
    myFixture.addClass("package com.intellij.testFramework;" +
                       "@interface TestDataPath { String value(); }")

    myFixture.configureByText("ATest.kt", """
      import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

      @com.intellij.testFramework.TestDataPath("${"\\\$"}CONTENT_ROOT/contentRootSubdir/")
      class ATest : LightCodeInsightFixtureTestCase {
        protected fun doTest() {
          configureByFile(getTestName(true) + ".java");
        }
      }
    """.trimIndent())

    assertResolvedTo(myContentRoot, "CONTENT_ROOT")
    assertResolvedTo(myContentRootSubdir, "contentRootSubdir")
  }

  private fun assertResolvedTo(virtualFile: VirtualFile, text: String) {
    TestCase.assertEquals(PsiManager.getInstance(myFixture.getProject()).findDirectory(virtualFile),
                          myFixture.file.getReferenceForText(text).resolve())
  }

  private fun PsiFile.getReferenceForText(text: String): PsiReference {
    val textPosition = this.text.indexOf(text).also {
      if (it == -1) throw AssertionError("text \"$text\" not found in \"$name\" ")
    }
    return findReferenceAt(textPosition + (text.length) / 2) ?: throw AssertionError(
      "reference not found at \"$name\" for text \"$text\" position = $textPosition "
    )
  }


}