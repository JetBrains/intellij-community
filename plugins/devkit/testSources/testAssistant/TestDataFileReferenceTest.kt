// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.testFramework.writeChild
import junit.framework.TestCase

class TestDataFileReferenceTest : TestDataReferenceTestCase() {

  fun testDataFileResolve() {
    val javaFile = myContentRootSubdir.writeChild("TestClass.java", "some java code here")

    myFixture.configureByText("ATest.java", """
      import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

      @com.intellij.testFramework.TestDataPath("${"\$"}CONTENT_ROOT/contentRootSubdir/")
      public class ATest extends LightCodeInsightFixtureTestCase {
        protected void doTest() {
          configureByFile("TestClass.java");
        }

        void configureByFile(@com.intellij.testFramework.TestDataFile String file){}
      }
    """.trimIndent())

    assertResolvedTo(javaFile, "TestClass.java")
  }

  fun testDataFileResolveVarags() {
    val testClass1 = myContentRootSubdir.writeChild("TestClass1.java", "some java code here")
    val testClass2 = myContentRootSubdir.writeChild("TestClass2.java", "some java code here")

    myFixture.configureByText("ATest.java", """
      import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

      @com.intellij.testFramework.TestDataPath("${"\$"}CONTENT_ROOT/contentRootSubdir/")
      public class ATest extends LightCodeInsightFixtureTestCase {
        protected void doTest() {
          configureByFiles("TestClass1.java", "TestClass2.java");
        }

        void configureByFiles(@com.intellij.testFramework.TestDataFile String... files){}
      }
    """.trimIndent())

    assertResolvedTo(testClass1, "TestClass1.java")
    assertResolvedTo(testClass2, "TestClass2.java")
  }

}


abstract class TestDataReferenceTestCase : TestDataPathTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.addClass("package com.intellij.testFramework; public @interface TestDataPath { String value(); }")
    myFixture.addClass("package com.intellij.testFramework; public @interface TestDataFile { }")
  }

  fun assertResolvedTo(virtualFile: VirtualFile, referenceTest: String) {
    val psiManager = PsiManager.getInstance(project)
    val fileSystemItem = if (virtualFile.isDirectory) psiManager.findDirectory(virtualFile) else psiManager.findFile(virtualFile)
    TestCase.assertEquals(fileSystemItem, myFixture.file.getReferenceForText(referenceTest).resolve())
  }

  fun PsiFile.getReferenceForText(text: String): PsiReference {
    val textPosition = this.text.indexOf(text).also {
      if (it == -1) throw AssertionError("text \"$text\" not found in \"$name\" ")
    }
    return findReferenceAt(textPosition + (text.length) / 2) ?: throw AssertionError(
      "reference not found at \"$name\" for text \"$text\" position = $textPosition "
    )
  }

}