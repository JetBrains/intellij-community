// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.testAssistant

import com.intellij.testFramework.writeChild
import org.jetbrains.idea.devkit.testAssistant.TestDataReferenceTestCase

class KtTestDataReferenceTest : TestDataReferenceTestCase() {

  fun testRootAndSubdir() {
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

  fun testDataFileResolve() {
    val javaFile = myContentRootSubdir.writeChild("TestClass.java", "some java code here")

    myFixture.configureByText("ATest.kt", """
      import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

      @com.intellij.testFramework.TestDataPath("${"\\\$"}CONTENT_ROOT/contentRootSubdir/")
      class ATest : LightCodeInsightFixtureTestCase {
        protected fun doTest() {
          configureByFile("TestClass.java");
        }

        fun configureByFile(@com.intellij.testFramework.TestDataFile file: String){}
      }
    """.trimIndent())

    assertResolvedTo(javaFile, "TestClass.java")

  }

}