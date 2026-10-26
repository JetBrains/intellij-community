// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.grazie.ide.language

import com.intellij.grazie.grazieTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class IgnoreSupportTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  @Suppress("unused")
  private val grazie by grazieTestFixture(projectFixture)

  @Test
  fun `test typo in gitignore comment`() {
    grazie.fixture.configureByText(
      "a.ignore",
      """
        # This is a <TYPO descr="Typo: In word 'frend'">frend</TYPO> of human
        target/
      """.trimIndent()
    )
    grazie.fixture.checkHighlighting()
  }

  @Test
  fun `test grammar error in gitignore comment`() {
    grazie.fixture.configureByText(
      "a.ignore",
      """
        # It is <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> friend of human
        build/
      """.trimIndent()
    )
    grazie.fixture.checkHighlighting()
  }

  @Test
  fun `test consecutive comment lines are joined`() {
    grazie.fixture.configureByText(
      "a.ignore",
      """
        # It is
        # <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> friend of human
        out/
      """.trimIndent()
    )
    grazie.fixture.checkHighlighting()
  }

  @Test
  fun `test no spellcheck on entries`() {
    grazie.fixture.configureByText(
      "a.ignore",
      """
        # comment
        frend/
        somemisspeltdir/*.log
      """.trimIndent()
    )
    grazie.fixture.checkHighlighting()
  }

  @Test
  fun `test section and header comments are checked`() {
    grazie.fixture.configureByText(
      "a.ignore",
      """
        ### Header with a <TYPO descr="Typo: In word 'typoheader'">typoheader</TYPO>

        ## Section with a <TYPO descr="Typo: In word 'typosection'">typosection</TYPO>

        # Regular with a <TYPO descr="Typo: In word 'typocomment'">typocomment</TYPO>
        build/
      """.trimIndent()
    )
    grazie.fixture.checkHighlighting()
  }
}
