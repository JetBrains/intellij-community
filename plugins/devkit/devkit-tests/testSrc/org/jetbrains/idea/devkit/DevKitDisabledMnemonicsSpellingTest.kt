// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit

import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
internal class DevKitDisabledMnemonicsSpellingTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Throws(Exception::class)
  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(GrazieSpellCheckingInspection::class.java)
  }

  @Test
  fun testMnemonics() {
    myFixture.configureByText("MyBundle.properties", """
      action.Annotate.text=A_<TYPO descr="Typo: In word 'nnotate'">nnotate</TYPO>
      before.check.cleanup.code=C&<TYPO descr="Typo: In word 'leanup'">leanup</TYPO>
      some.property=GRADLE_USER_HOME
    """.trimIndent())
    myFixture.checkHighlighting()
  }
}
