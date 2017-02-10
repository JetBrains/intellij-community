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
package org.jetbrains.plugins.groovy.lang.parser

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author peter
 */
class GroovyReparseTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "reparse/"
  }

  void checkReparse(String text, String type) {
    myFixture.configureByText("a.groovy", text)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    final String psiBefore = DebugUtil.psiToString(myFixture.getFile(), false)

    myFixture.type(type)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    final String psiAfter = DebugUtil.psiToString(myFixture.getFile(), false)

    myFixture.configureByText("a.txt", psiBefore.trim() + "\n---\n" + psiAfter.trim())
    myFixture.checkResultByFile(getTestName(false) + ".txt")
  }

  void testCodeBlockReparse() throws IOException {
    checkReparse("foo 'a', {<caret>}", '\n')
  }

  void testSwitchCaseIf() throws Exception {
    checkReparse """
  def foo() {
    switch(x) {
      case 2:
      <caret>return 2
    }
  }
""", "if "
  }

  void testSwitchCaseDef() throws Exception {
    checkReparse """
  def foo() {
    switch(x) {
      case 2:
      <caret>return 2
    }
  }
""", "def "
  }

  void testSwitchCaseFor() throws Exception {
    checkReparse """
  def foo() {
    switch(x) {
      case 2:
      <caret>return 2
    }
  }
""", "for "
  }

  void testSwitchCaseWhile() throws Exception {
    checkReparse """
  def foo() {
    switch(x) {
      case 2:
      <caret>return 2
    }
  }
""", "while "
  }

  void testSwitchCaseDo() throws Exception {
    checkReparse """
  def foo() {
    switch(x) {
      case 2:
      <caret>return 2
    }
  }
""", "doo "
  }

  void testSwitchCaseSwitch() throws Exception {
    checkReparse """
  def foo() {
    switch(x) {
      case 2:
      <caret>return 2
    }
  }
""", "switch "
  }

  void testSwitchCaseDot() throws Exception {
    checkReparse """
  def foo() {
    switch(x) {
      case 2:
        return <caret>
      case 3:
        return false
      case 4:
        return false
    }
  }
""", "foo."
  }

  void testOpeningParenthesisAtBlockStart() {
    checkReparse """
def foo() {
    <caret>String home
    simplePlugins.each {
        layoutPlugin it
    }

}
}""", "("
  }

  void testNoVariableName() {
    checkReparse """
def foo() {
    switch (w) {
      case 2:
        def x<caret> = xxx
        if () {
        }
        return 2

    }
    return state
  }
""", "\b"
  }

  void testSwitchRParen() {
    checkReparse """
def foo() {
    switch (word w w)<caret> {
      case 2:
        def x = (y)
    }
  }
""", "\b"
  }

  void testWhileRParen() {
    checkReparse """
def foo() {
  def cl = {
    while (true<caret> {
      //
    }
  }
}""", ";"
  }

  void testSynchronizedRParen() {
    checkReparse """
def foo() {
  def cl = {
    synchronized (x<caret> {
      //
    }
  }
}""", ";"
  }

  void testMultilineToNormalString() {
    checkReparse '''
class a {
  def foo() {
    bar(""<caret>aaa")
  }

  def bar() {
    zoo()
  }
}
''', '\b'
  }


}
