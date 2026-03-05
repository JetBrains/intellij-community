// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.testAssistant

import com.intellij.testFramework.writeChild
import org.jetbrains.idea.devkit.testAssistant.TestDataReferenceTestCase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

class KtTestDataReferenceTest : TestDataReferenceTestCase(), ExpectedPluginModeProvider {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
  override fun setUp() {
    setUpWithKotlinPlugin { super.setUp() }
  }

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