// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieTestBase
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase


class JavaSupportTest : GrazieTestBase() {
  override val additionalEnabledRules: Set<String> = setOf("LanguageTool.EN.UPPERCASE_SENTENCE_START")

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return LightJavaCodeInsightFixtureTestCase.JAVA_LATEST
  }

  fun `test spellcheck in constructs`() {
    runHighlightTestForFile("ide/language/java/Constructs.java")
  }

  fun `test grammar check in docs`() {
    runHighlightTestForFile("ide/language/java/Docs.java")
  }

  fun `test grammar check in string literals`() {
    runHighlightTestForFile("ide/language/java/StringLiterals.java")
  }

  fun `test grammar check in comments`() {
    runHighlightTestForFile("ide/language/java/Comments.java")
  }

  fun `test split line quick fix`() {
    runHighlightTestForFile("ide/language/java/SplitLine.java")
    myFixture.launchAction(myFixture.findSingleIntention(", so"))
    myFixture.checkResultByFile("ide/language/java/SplitLine_after.java")
  }

  fun `test long comment performance`() {
    PlatformTestUtil.startPerformanceTest("highlighting", 1000) {
      runHighlightTestForFile("ide/language/java/LongCommentPerformance.java")
    }.setup { psiManager.dropPsiCaches() }.usesAllCPUCores().assertTiming()
  }

  fun `test performance with many line comments`() {
    val text = "// this is a single line comment\n".repeat(5000)
    myFixture.configureByText("a.java", text)
    PlatformTestUtil.startPerformanceTest("highlighting", 2000) {
      myFixture.checkHighlighting()
    }.setup { psiManager.dropPsiCaches() }.usesAllCPUCores().assertTiming()
  }
}
