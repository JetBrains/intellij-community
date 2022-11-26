// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

class GroovyReparseTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "reparse/"
  }

  void checkReparse(String text, String type) {
    myFixture.configureByText("a.groovy", text)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    final String psiBefore = DebugUtil.psiToString(myFixture.getFile(), true)

    myFixture.type(type)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    final String psiAfter = DebugUtil.psiToString(myFixture.getFile(), true)

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

  void testNewLinesBetweenBlocks() {
    checkReparse '''\
class A {

  void foo() {
    id0(id1{id2
  }

  <selection> {
    id3(}</selection>
  }

  void bar() {}
}
''', '\b'
  }
}
