// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.grazieTestFixture
import com.intellij.grazie.jlanguage.Lang
import com.intellij.lang.xml.XMLLanguage
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test


@TestApplication
class XMLSupportTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val grazie by grazieTestFixture(projectFixture, additionalEnabledRules = setOf("LanguageTool.EN.EN_QUOTES"))

  @Test
  fun `test grammar check in xsd file`() {
    grazie.runHighlightTestForFile("ide/language/xml/Example.xsd")
  }

  @Test
  fun `test grammar check in xml file`() {
    grazie.enableProofreadingFor(setOf(Lang.RUSSIAN))
    grazie.runHighlightTestForFile("ide/language/xml/Example.xml")
  }

  @Test
  fun `test typo checks and self references xml file`() {
    grazie.enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.PORTUGAL_PORTUGUESE))
    grazie.runHighlightTestForFile("ide/language/xml/SelfReferenceExample.xml")
  }

  @Test
  fun `test typo checks when comments are before root tag xml file`() {
    grazie.runHighlightTestForFile("ide/language/xml/Comment.xml")
  }

  @Test
  fun `test no grammar checks in svg file`() {
    grazie.runHighlightTestForFile("ide/language/xml/Example.svg")
  }

  @Test
  fun `test grammar check in html file`() {
    grazie.enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.RUSSIAN))
    GrazieConfig.update { it.copy(checkingContext = it.checkingContext.copy(disabledLanguages = setOf(XMLLanguage.INSTANCE.id))) }
    grazie.runHighlightTestForFile("ide/language/xml/Example.html")
  }

  @Test
  fun `test typo checks and self references html file`() {
    grazie.enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.PORTUGAL_PORTUGUESE))
    grazie.runHighlightTestForFile("ide/language/xml/SelfReferenceExample.html")
  }

  @Test
  fun `test typo checks when comments are before root tag html file`() {
    grazie.runHighlightTestForFile("ide/language/xml/Comment.html")
  }

  @Test
  fun `test grazie spellchecking in html file`() {
    grazie.runHighlightTestForFile("ide/language/xml/Spellcheck.html")
  }
}
