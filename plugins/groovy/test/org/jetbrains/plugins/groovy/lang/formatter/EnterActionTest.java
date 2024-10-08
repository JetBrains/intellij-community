/*
 * Copyright 2003-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.formatter

import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings
import org.jetbrains.plugins.groovy.util.TestUtils
class EnterActionTest extends GroovyFormatterTestCase {

  final String basePath = TestUtils.testDataPath + 'groovy/enterAction/'

  void doTest() throws Throwable {
    final List<String> data = TestUtils.readInput(testDataPath + getTestName(true) + ".test")
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, data.get(0))
    doEnter()
    myFixture.checkResult(data.get(1))
  }

  private def doEnter() {
    myFixture.type('\n' as char)
  }

  void testClos1() throws Throwable { doTest() }

  void testClos2() throws Throwable { doTest() }

  void testComment1() throws Throwable { doTest() }

  void testComment2() throws Throwable { doTest() }

  void testComment3() throws Throwable { doTest() }

  void testComment4() throws Throwable { doTest() }

  void testDef() throws Throwable { doTest() }

  void testDef2() throws Throwable { doTest() }

  void testGdoc1() throws Throwable { doTest() }

  void testGdoc2() throws Throwable { doTest() }

  void testGdoc3() throws Throwable { doTest() }

  void testGdoc4() throws Throwable { doTest() }

  void testGdoc5() throws Throwable { doTest() }

  void testGdoc6() throws Throwable { doTest() }

  void testGdoc7() throws Throwable { doTest() }

  void testGdoc8() throws Throwable { doTest() }

  void testGdoc9() throws Throwable { doTest() }

  void testGdoc10() throws Throwable { doTest() }

  void testGdoc11() throws Throwable { doTest() }

  void testBuildGdocPrecededByGNewLine() throws Throwable { doTest() }

  void testGRVY_953() throws Throwable { doTest() }

  void testGstring1() throws Throwable { doTest() }

  void testGstring10() throws Throwable { doTest() }

  void testGstring11() throws Throwable { doTest() }

  void testGstring12() throws Throwable { doTest() }

  void testGstring13() throws Throwable { doTest() }

  void testGstring14() throws Throwable { doTest() }

  void testGstring15() throws Throwable { doTest() }

  void testGstring16() throws Throwable { doTest() }

  void testGstring2() throws Throwable { doTest() }

  void testGstring3() throws Throwable { doTest() }

  void testGstring4() throws Throwable { doTest() }

  void testGstring5() throws Throwable { doTest() }

  void testGstring6() throws Throwable { doTest() }

  void testGstring7() throws Throwable { doTest() }

  void testGstring8() throws Throwable { doTest() }

  void testGstring9() throws Throwable { doTest() }

  void testMultilineIndent() throws Throwable { doTest() }

  void testMultilineIndent2() throws Throwable { doTest() }

  void testSpaces1() throws Throwable { doTest() }

  void testString1() throws Throwable { doTest() }

  void testString2() throws Throwable { doTest() }

  void testString3() throws Throwable { doTest() }

  void testString4() throws Throwable { doTest() }

  void testString5() throws Throwable { doTest() }

  void testString6() throws Throwable { doTest() }

  void testString7() throws Throwable { doTest() }

  void testString8() throws Throwable { doTest() }

  void testString9() throws Throwable { doTest() }

  void testString10() throws Throwable { doTest() }

  void testRegex1() { doTest() }

  void testRegex2() { doTest() }

  void testRegex3() { doTest() }

  void testRegex4() { doTest() }

  void testRegex5() { doTest() }

  void testRegex6() { doTest() }

  void testRegex7() { doTest() }

  void testRegex8() { doTest() }

  void testRegex9() { doTest() }

  void testRegex10() { doTest() }

  def doTest(String before, String after) {
    myFixture.configureByText("a.groovy", before)
    doEnter()
    myFixture.checkResult(after, true)
  }

  void testAfterClosureArrow() throws Throwable {
    doTest """
def c = { a -><caret> }
""", """
def c = { a ->
  <caret>
}
"""
  }

  void testAfterClosureArrowWithBody() throws Throwable {
    doTest """
def c = { a -><caret> zzz }
""", """
def c = { a ->
<caret>  zzz }
"""
  }

  void testAfterClosureArrowWithBody2() throws Throwable {
    doTest """
def c = { a -> <caret>zzz }
""", """
def c = { a ->
  <caret>zzz }
"""
  }

  void testBeforeClosingClosureBrace() throws Throwable {
    doTest """
def c = { a ->
  zzz <caret>}
""", """
def c = { a ->
  zzz 
<caret>}
"""
  }

  //TODO: IDEA-207401
  void _testAfterLambdaArrow() throws Throwable {
    doTest """
def c = a -><caret>
""", """
def c = a ->
    <caret>
"""
  }

  void testAfterLambdaArrowWithExpressionBody() throws Throwable {
    doTest """
def c = a -><caret> zzz
""", """
def c = a ->
<caret>    zzz
"""
  }

  void testAfterLambdaArrowWithExpressionBody2() throws Throwable {
    doTest """
def c = a -> <caret>zzz
""", """
def c = a ->
    <caret>zzz
"""
  }
  void testAfterLambdaArrowWithBlockBody() throws Throwable {
    doTest """
def c = a -> {<caret> zzz}
""", """
def c = a -> {
  <caret>zzz}
"""
  }

  void testAfterLambdaArrowWithBlockBody2() throws Throwable {
    doTest """
def c = a -> {<caret>}
""", """
def c = a -> {
  <caret>
}
"""
  }

  void testBeforeClosingLambdaBrace() throws Throwable {
    doTest """
def c = a -> {
  zzz <caret>}
""", """
def c = a -> { 
  zzz 
<caret>}
"""
  }

  void testAfterCase() {
    doTest """
switch(x) {
  case 0: return x
  case 1:<caret>
}""", """
switch(x) {
  case 0: return x
  case 1:
    <caret>
}"""
  }

  void testCaseBeforeReturn() {
    doTest """
switch(x) {
  case 0: <caret>
    return x
}""", """
switch(x) {
  case 0:
    <caret>
    return x
}"""
  }

  void testCaseAfterBreak() {
    doTest """
switch(x) {
  case 0:
    break<caret>
}""", """
switch(x) {
  case 0:
    break
  <caret>
}"""
  }

  void testCaseAfterCall() {
    doTest """
switch(x) {
  case 0:
    foo()<caret>
}""", """
switch(x) {
  case 0:
    foo()
    <caret>
}"""
  }

  void testCaseAfterReturn() {
    doTest """
switch(x) {
  case 0:
    return 2<caret>
}""", """
switch(x) {
  case 0:
    return 2
  <caret>
}"""
  }

  void testWrapInSwitchBlock() {
    doTest """
switch(x) {
  case 0 -> {<caret>}
}
""", """
switch(x) {
  case 0 -> {
    <caret>
  }
}
"""
  }

  void testWrapAfterSwitchArrowedExpression() {
    doTest """
switch(x) {
  case 0 -> zzz<caret>
}
""", """
switch(x) {
  case 0 -> zzz
  <caret>
}
"""
  }

  void testWrapInSwitchExpressionList() {
    doTest """
switch(x) {
  case 0, <caret>10 -> zzz
}
""", """
switch(x) {
  case 0, 
      <caret>10 -> zzz
}
"""
  }

  void testAlmostBeforeClosingClosureBrace() throws Throwable {
    doTest  """
def c = { a ->
  zzz<caret> }
""", """
def c = { a ->
  zzz
<caret>}
"""
  }

  void testEnterWithAlignedParameters() {
    groovySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    doTest """foo(2,<caret>)
""", """foo(2,
    <caret>)
"""

  }

  void testEnterWithAlignedParameters2() {
      groovySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    doTest """foo(2,<caret>
""", """foo(2,
    <caret>
"""

  }

  void testEnterAfterAssignmentInDeclaration() {
    doTest """def greeting = <caret>
""", """def greeting =
    <caret>
"""
  }

  void testEnterInAssignment() {
    doTest """greeting = <caret>
""", """greeting =
    <caret>
"""

  }

  void testEnterInIf() {
    doTest """if (2==2) <caret>
""", """if (2==2)
  <caret>
"""

  }

  void testGeese1() {
    myTempSettings.getCustomSettings(GroovyCodeStyleSettings).USE_FLYING_GEESE_BRACES = true
    doTest '''[1, 2, 3, 4].
    toSet().
    findAllXxx {
      it > 2
      foo {<caret>}
    }''',
            '''[1, 2, 3, 4].
    toSet().
    findAllXxx {
      it > 2
      foo {
        <caret>
    } }'''

  }

  void testGeese2() {
    myTempSettings.getCustomSettings(GroovyCodeStyleSettings).USE_FLYING_GEESE_BRACES = true
    doTest '''foo {
  bar {<caret>}
}
''', '''foo {
  bar {
    <caret>
} }
'''
  }

  void testGeese3() {
    myTempSettings.getCustomSettings(GroovyCodeStyleSettings).USE_FLYING_GEESE_BRACES = true
    doTest '''\
foo {
  [1].bar {<caret>}
}
''', '''\
foo {
  [1].bar {
    <caret>
} }
'''
  }

  void testEnterAfterSlash() {
    doTest """\
print '\\<caret>'
""", """\
print '\\
<caret>'
"""
  }

  void testEnterAfterSlash2() {
    doTest '''\
print "\\<caret>"
''', '''\
print "\\
<caret>"
'''
  }

  void "test enter after doc with wrong braces"() {
    doTest '''
/**
 * {@link #z
 */
protected void z() {<caret>
}''', '''
/**
 * {@link #z
 */
protected void z() {
  <caret>
}'''
  }

  void testAfterArrow() {
    doTest '''\
def cl =  {
  String s -><caret>
}
''', '''\
def cl =  {
  String s ->
    <caret>
}
'''
  }

  void testIndentAfterLabelColon() {
    groovySettings.indentOptions.LABEL_INDENT_SIZE = 2
    groovySettings.indentOptions.INDENT_SIZE = 2
    groovyCustomSettings.INDENT_LABEL_BLOCKS = true
    doTest('''
class A extends spock.lang.Specification {
  def 'test'() {
    abc:<caret>
  }
}
''', '''
class A extends spock.lang.Specification {
  def 'test'() {
    abc:
      <caret>
  }
}
''')
  }

  void testCommentAtFileEnd() {
    doTest('''\
print 2
/*<caret>''', '''\
print 2
/*
<caret>
 */''')
  }

  void testIfCondition() {
    doTest('if (<caret>true) {}', '''\
if (
    true) {}''')
  }
}

