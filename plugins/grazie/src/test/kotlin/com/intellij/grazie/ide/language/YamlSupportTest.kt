// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextExtractor

class YamlSupportTest : GrazieTestBase() {
  fun `test grammar check in yaml file`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.RUSSIAN))
    runHighlightTestForFile("ide/language/yaml/Example.yaml")
  }

  fun `test text extraction`() {
    val file = myFixture.configureByText("a.yaml", "foo: 'bar'")
    assertEquals("bar", TextExtractor.findTextAt(file, 6, TextContent.TextDomain.ALL).toString())
  }
}
