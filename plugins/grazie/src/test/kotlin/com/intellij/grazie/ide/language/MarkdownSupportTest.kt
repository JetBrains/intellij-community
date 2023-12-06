// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieTestBase


class MarkdownSupportTest : GrazieTestBase() {
  override val additionalEnabledRules: Set<String> = setOf(
    "LanguageTool.EN.UPPERCASE_SENTENCE_START",
    "LanguageTool.EN.COMMA_COMPOUND_SENTENCE",
    "LanguageTool.EN.EN_QUOTES"
  )

  fun `test grammar check in file`() {
    runHighlightTestForFile("ide/language/markdown/Example.md")
  }

  fun `test replacement with markup inside`() {
    myFixture.configureByText("a.md", "Please, <caret><warning>gather </warning>[<warning>up</warning> the](url) documentation.")
    myFixture.checkHighlighting()
    myFixture.launchAction(myFixture.findSingleIntention("gather"))
    myFixture.checkResult("Please, gather[ the](url) documentation.") // the result could be different, but the markup should still be preserved
  }
}
