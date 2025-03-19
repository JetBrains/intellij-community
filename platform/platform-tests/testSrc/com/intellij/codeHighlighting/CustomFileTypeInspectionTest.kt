// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeHighlighting

import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CustomFileTypeInspectionTest : BasePlatformTestCase() {

  fun testSpellChecking() {
    myFixture.configureByText("a.hs", """

infixl -- keyword
<TYPO descr="Typo: In word 'sdaf'">sdaf</TYPO> -- identifier
"<TYPO descr="Typo: In word 'infixl'">infixl</TYPO> in string"
-- <TYPO descr="Typo: In word 'infixl'">infixl</TYPO> in line comment
{- <TYPO descr="Typo: In word 'infixl'">infixl</TYPO> in block comment -}
""")
    myFixture.enableInspections(SpellCheckingInspection())
    myFixture.checkHighlighting()
  }

}