/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeHighlighting

import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import groovy.transform.CompileStatic

@CompileStatic
class CustomFileTypeInspectionTest extends BasePlatformTestCase {

  void testSpellChecking() {
    myFixture.configureByText 'a.hs', '''

infixl -- keyword
<TYPO descr="Typo: In word 'sdaf'">sdaf</TYPO> -- identifier
"<TYPO descr="Typo: In word 'infixl'">infixl</TYPO> in string"
-- <TYPO descr="Typo: In word 'infixl'">infixl</TYPO> in line comment
{- <TYPO descr="Typo: In word 'infixl'">infixl</TYPO> in block comment -}
'''
    myFixture.enableInspections(new SpellCheckingInspection())
    myFixture.checkHighlighting()
  }
  
}
