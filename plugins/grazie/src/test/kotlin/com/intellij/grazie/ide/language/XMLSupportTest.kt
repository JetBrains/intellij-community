// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieTestBase
import com.intellij.lang.xml.XMLLanguage


class XMLSupportTest : GrazieTestBase() {
  override val additionalEnabledRules: Set<String> = setOf("LanguageTool.EN.EN_QUOTES")

  fun `test grammar check in xsd file`() {
    runHighlightTestForFile("ide/language/xml/Example.xsd")
  }

  fun `test grammar check in xml file`() {
    runHighlightTestForFile("ide/language/xml/Example.xml")
  }

  fun `test no grammar checks in svg file`() {
    runHighlightTestForFile("ide/language/xml/Example.svg")
  }

  fun `test grammar check in html file`() {
    GrazieConfig.update { it.copy(checkingContext = it.checkingContext.copy(disabledLanguages = setOf(XMLLanguage.INSTANCE.id))) }
    runHighlightTestForFile("ide/language/xml/Example.html")
  }

}
