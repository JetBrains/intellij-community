// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieTestBase
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase


class JavaSupportTest : GrazieTestBase() {
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
}
