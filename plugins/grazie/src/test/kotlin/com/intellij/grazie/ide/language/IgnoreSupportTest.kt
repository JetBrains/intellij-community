// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieTestBase

class IgnoreSupportTest : GrazieTestBase() {

  fun `test typo in gitignore comment`() {
    myFixture.configureByText(
      "a.ignore",
      """
        # This is a <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human
        target/
      """.trimIndent()
    )
    myFixture.checkHighlighting()
  }

  fun `test grammar error in gitignore comment`() {
    myFixture.configureByText(
      "a.ignore",
      """
        # It is <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> friend of human
        build/
      """.trimIndent()
    )
    myFixture.checkHighlighting()
  }

  fun `test consecutive comment lines are joined`() {
    myFixture.configureByText(
      "a.ignore",
      """
        # It is
        # <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> friend of human
        out/
      """.trimIndent()
    )
    myFixture.checkHighlighting()
  }

  fun `test no spellcheck on entries`() {
    myFixture.configureByText(
      "a.ignore",
      """
        # comment
        frend/
        somemisspeltdir/*.log
      """.trimIndent()
    )
    myFixture.checkHighlighting()
  }

  fun `test section and header comments are checked`() {
    myFixture.configureByText(
      "a.ignore",
      """
        ### Header with a <TYPO descr="Typo: In word 'typoheader'">typoheader</TYPO>

        ## Section with a <TYPO descr="Typo: In word 'typosection'">typosection</TYPO>

        # Regular with a <TYPO descr="Typo: In word 'typocomment'">typocomment</TYPO>
        build/
      """.trimIndent()
    )
    myFixture.checkHighlighting()
  }
}
