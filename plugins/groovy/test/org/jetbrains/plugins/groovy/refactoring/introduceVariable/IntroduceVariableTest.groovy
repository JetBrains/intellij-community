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

package org.jetbrains.plugins.groovy.refactoring.introduceVariable

import com.intellij.psi.PsiType
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GrIntroduceVariableHandler
import org.jetbrains.plugins.groovy.util.TestUtils
class IntroduceVariableTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.testDataPath + "groovy/refactoring/introduceVariable/"
  }

  void testAbs() { doTest() }

  void testCall1() { doTest() }

  void testCall2() { doTest() }

  void testCall3() { doTest() }

  void testClos1() { doTest() }

  void testClos2() { doTest() }

  void testClos3() { doTest() }

  void testClos4() { doTest() }

  void testF2() { doTest() }

  void testField1() { doTest() }

  void testFirst() { doTest() }

  void testIf1() { doTest() }

  void testIf2() { doTest() }

  void testLocal1() { doTest() }

  void testLoop1() { doTest() }

  void testLoop2() { doTest() }

  void testLoop3() { doTest() }

  void testLoop4() { doTest() }

  void testLoop5() { doTest() }

  void testLoop6() { doTest() }

  void testLoop7() { doTest() }

  void testLoop8() { doTest() }

  void testInCase() { doTest() }

  void testCaseLabel() { doTest() }

  void testLabel1() { doTest() }

  void testLabel2() { doTest() }

  void testLabel3() { doTest() }

  void testDuplicatesInsideIf() { doTest() }

  void testFromGString() { doTest() }

  void testCharArray() { doTest(true) }

  void testCallableProperty() { doTest() }

  void testFqn() {
    myFixture.addClass('''\
package p;
public class Foo {
    public static int foo() {
        return 1;
    }
}
''')
    doTest()
  }

  void testStringPart1() {
    doTest('''\
print 'a<begin>b<end>c'
''', '''\
def preved = 'b'
print 'a' + preved<caret> + 'c'
''')
  }

  void testStringPart2() {
    doTest('''\
print "a<begin>b<end>c"
''', '''\
def preved = "b"
print "a" + preved<caret> + "c"
''')
  }

  void testAllUsages() {
    doTest('''\
def foo() {
    println(123);        // (1)
    println(123);        // (2)
    if (true) {
        println(<all>123<end>);    // (3)
        println(123);    // (4)
    }
}
''', '''\
def foo() {
    def preved = 123
    println(preved);        // (1)
    println(preved);        // (2)
    if (true) {
        println(preved<caret>);    // (3)
        println(preved);    // (4)
    }
}
''')
  }

  void testDollarSlashyString() {
    doTest('''\
print($/a<begin>b<end>c/$)
''', '''\
def preved = $/b/$
print($/a/$ + preved + $/c/$)
''')
  }

  protected static final String ALL_MARKER = "<all>"

  private void processFile(String fileText, boolean explicitType) {
    boolean replaceAllOccurrences = prepareText(fileText)

    PsiType type = inferType(explicitType)

    final MockSettings settings = new MockSettings(false, "preved", type, replaceAllOccurrences)
    final GrIntroduceVariableHandler introduceVariableHandler = new MockGrIntroduceVariableHandler(settings)

    introduceVariableHandler.invoke(myFixture.project, myFixture.editor, myFixture.file, null)
  }

  private boolean prepareText(@NotNull String fileText) {
    int startOffset = fileText.indexOf(TestUtils.BEGIN_MARKER)

    boolean replaceAllOccurrences
    if (startOffset < 0) {
      startOffset = fileText.indexOf(ALL_MARKER)
      replaceAllOccurrences = true
      fileText = removeAllMarker(fileText)
    }
    else {
      replaceAllOccurrences = false
      fileText = TestUtils.removeBeginMarker(fileText)
    }

    int endOffset = fileText.indexOf(TestUtils.END_MARKER)
    fileText = TestUtils.removeEndMarker(fileText)

    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText)

    myFixture.editor.selectionModel.setSelection(startOffset, endOffset)
    replaceAllOccurrences
  }

  private PsiType inferType(boolean explicitType) {
    if (explicitType) {
      final int start = myFixture.editor.selectionModel.selectionStart
      final int end = myFixture.editor.selectionModel.selectionEnd
      final GrExpression expression = GrIntroduceHandlerBase.findExpression(myFixture.file, start, end)
      if (expression != null) {
        return expression.type
      }
    }
    return null
  }

  void doTest(boolean explicitType = false) {
    def (String before, String after) = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test")
    doTest(before, after, explicitType)
  }

  void doTest(String before, String after, boolean explicitType = false) {
    processFile(before, explicitType)
    myFixture.checkResult(after, true)
  }

  protected static String removeAllMarker(String text) {
    int index = text.indexOf(ALL_MARKER)
    return text.substring(0, index) + text.substring(index + ALL_MARKER.length())
  }

}
