// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieTestBase


class MarkdownSupportTest : GrazieTestBase() {
  override val additionalEnabledRules: Set<String> = setOf("LanguageTool.EN.UPPERCASE_SENTENCE_START")

  fun `test grammar check in file`() {
    runHighlightTestForFile("ide/language/markdown/Example.md")
  }

  fun `test replacement with markup inside`() {
    myFixture.configureByText("a.md", "<warning>First </warning>[<warning>of all</warning>, it's a great](url) sentence.")
    myFixture.checkHighlighting()
    myFixture.launchAction(myFixture.findSingleIntention("First"))
    myFixture.checkResult("First[, it's a great](url) sentence.") // the result could be different, but the markup should still be preserved
  }
}
