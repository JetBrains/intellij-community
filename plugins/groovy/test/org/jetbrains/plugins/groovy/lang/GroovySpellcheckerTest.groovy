/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.spellchecker.inspections.SpellCheckingInspection

class GroovySpellcheckerTest extends LightJavaCodeInsightFixtureTestCase {

  void testParameterName() {
    myFixture.configureByText 'a.groovy', '''
def test(int <TYPO descr="Typo: In word 'ddddd'">ddddd</TYPO>) {
}
'''
    checkTypos()
  }

  private checkTypos() {
    myFixture.enableInspections(new SpellCheckingInspection())
    myFixture.checkHighlighting(false, false, true)
  }

  void testLiteralMethodNames() {
    myFixture.configureByText 'a.groovy', '''
class SpockTest {
  def "adds a 'play' extension"() { }
  def "<TYPO descr="Typo: In word 'addds'">addds</TYPO> a 'play' extension"() { }
}
'''
    checkTypos()
  }

  void testStringEscapes() {
    myFixture.configureByText 'a.groovy', '''
def foo = "\\ntest \\n<TYPO descr="Typo: In word 'ddddd'">ddddd</TYPO>"
def foo1 = '\\ntest \\n<TYPO descr="Typo: In word 'ddddd'">ddddd</TYPO>'
def bar = """\\ntest \\n<TYPO descr="Typo: In word 'ddddd'">ddddd</TYPO>"""
def bar1 = \'''\\ntest \\n<TYPO descr="Typo: In word 'ddddd'">ddddd</TYPO>\'''
'''
    checkTypos()
  }

}
