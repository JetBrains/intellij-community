// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.lang.xml.XMLLanguage


class XMLSupportTest : GrazieTestBase() {
  override val additionalEnabledRules: Set<String> = setOf("LanguageTool.EN.EN_QUOTES")

  fun `test grammar check in xsd file`() {
    runHighlightTestForFile("ide/language/xml/Example.xsd")
  }

  fun `test grammar check in xml file`() {
    enableProofreadingFor(setOf(Lang.RUSSIAN))
    runHighlightTestForFile("ide/language/xml/Example.xml")
  }

  fun `test typo checks and self references xml file`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.PORTUGAL_PORTUGUESE))
    runHighlightTestForFile("ide/language/xml/SelfReferenceExample.xml")
  }

  fun `test typo checks when comments are before root tag xml file`() {
    runHighlightTestForFile("ide/language/xml/Comment.xml")
  }

  fun `test no grammar checks in svg file`() {
    runHighlightTestForFile("ide/language/xml/Example.svg")
  }

  fun `test grammar check in html file`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.RUSSIAN))
    GrazieConfig.update { it.copy(checkingContext = it.checkingContext.copy(disabledLanguages = setOf(XMLLanguage.INSTANCE.id))) }
    runHighlightTestForFile("ide/language/xml/Example.html")
  }

  fun `test typo checks and self references html file`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.PORTUGAL_PORTUGUESE))
    runHighlightTestForFile("ide/language/xml/SelfReferenceExample.html")
  }

  fun `test typo checks when comments are before root tag html file`() {
    runHighlightTestForFile("ide/language/xml/Comment.html")
  }

  fun `test grazie spellchecking in html file`() {
    runHighlightTestForFileUsingGrazieSpellchecker("ide/language/xml/Spellcheck.html")
  }
}
