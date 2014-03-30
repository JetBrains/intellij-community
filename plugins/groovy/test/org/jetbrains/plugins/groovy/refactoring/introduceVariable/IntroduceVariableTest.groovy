/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceHandlerBase
import org.jetbrains.plugins.groovy.refactoring.introduce.variable.GrIntroduceVariableHandler
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author ilyas
 */
public class IntroduceVariableTest extends LightCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.testDataPath + "groovy/refactoring/introduceVariable/"
  }

  public void testAbs()       { doTest() }
  public void testCall1()     { doTest() }
  public void testCall2()     { doTest() }
  public void testCall3()     { doTest() }
  public void testClos1()     { doTest() }
  public void testClos2()     { doTest() }
  public void testClos3()     { doTest() }
  public void testClos4()     { doTest() }
  public void testF2()        { doTest() }
  public void testField1()    { doTest() }
  public void testFirst()     { doTest() }
  public void testIf1()       { doTest() }
  public void testIf2()       { doTest() }
  public void testLocal1()    { doTest() }
  public void testLoop1()     { doTest() }
  public void testLoop2()     { doTest() }
  public void testLoop3()     { doTest() }
  public void testLoop4()     { doTest() }
  public void testLoop5()     { doTest() }
  public void testLoop6()     { doTest() }
  public void testLoop7()     { doTest() }
  public void testLoop8()     { doTest() }
  public void testInCase()    { doTest() }
  public void testCaseLabel() { doTest() }
  public void testLabel1()    { doTest() }
  public void testLabel2()    { doTest() }
  public void testLabel3()    { doTest() }

  public void testDuplicatesInsideIf() { doTest() }
  public void testFromGString() { doTest() }

  public void testCharArray() {doTest(true) }

  public void testCallableProperty() {doTest() }

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

  public void doTest(boolean explicitType = false) {
    def (String before, String after) = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test")
    doTest(before, after, explicitType)
  }

  public void doTest(String before, String after, boolean explicitType = false) {
    processFile(before, explicitType)
    myFixture.checkResult(after, true)
  }

  protected static String removeAllMarker(String text) {
    int index = text.indexOf(ALL_MARKER)
    return text.substring(0, index) + text.substring(index + ALL_MARKER.length())
  }

}
