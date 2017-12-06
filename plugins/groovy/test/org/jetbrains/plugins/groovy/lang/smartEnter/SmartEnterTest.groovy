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
package org.jetbrains.plugins.groovy.lang.smartEnter

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * User: Dmitry.Krasilschikov
 */
class SmartEnterTest extends LightGroovyTestCase {

  final String basePath = TestUtils.testDataPath + "groovy/actions/smartEnter/"

  void testMethCallComma() { doTest() }

  void testMethCallWithArg() { doTest() }

  void testMethodCallMissArg() { doTest() }

  void testMissBody() { doTest() }

  void testMissCondition() { doTest() }

  void testMissIfclosureParen() { doTest() }

  void testMissIfCurl() { doTest() }

  void testMissingIfClosedParenth() { doTest() }

  void testMissRParenth() { doTest() }

  void testMissRParenthInMethod() { doTest() }

  void testMissRQuote() { doTest() }

  void testMissRQuoteInCompStr() { doTest() }

  void testGotoNextLineInFor() { doTest() }

  void testGotoParentInIf() { doTest() }

  void testListFixer() { doTest() }

  void testSwitchBraces() { doTest() }

  void testCatchClause() { doTest() }

  void testMethodBodyAtNextLine() {
    CodeStyleSettingsManager.getSettings(myFixture.project).getCommonSettings(GroovyLanguage.INSTANCE).METHOD_BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    doTest()
  }

  void testReturnMethodCall() {
    doTextTest('''\
class Foo {
    def bar

    def h(Foo o) {
        return compareTo(o.bar<caret>
    }
}
''', '''\
class Foo {
    def bar

    def h(Foo o) {
        return compareTo(o.bar)
        <caret>
    }
}
''')}

  void testSmartEnterInClosureArg() {
    doTextTest('''\
[1, 2, 3].each<caret>
''', '''\
[1, 2, 3].each {
    <caret>
}
''')
  }

  void testSynchronizedBraces() {
    doTextTest('''\
synchronized(x<caret>)
''', '''\
synchronized (x) {
    <caret>
}
''')
  }

  void testClassBody() {
    doTextTest('class X<caret>', '''\
class X {
    <caret>
}''')
  }

  private void doTextTest(String before, String after) {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, before)
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT)
    myFixture.checkResult(after)
  }

  private void doTest() {
    final List<String> data = TestUtils.readInput(testDataPath + getTestName(true) + ".test")
    doTextTest(data.get(0), data.get(1))
  }

}
