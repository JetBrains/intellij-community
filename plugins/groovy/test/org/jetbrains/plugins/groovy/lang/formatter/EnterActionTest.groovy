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
/**
 * @author ilyas
 */
public class EnterActionTest extends GroovyFormatterTestCase {

  final String basePath = TestUtils.testDataPath + 'groovy/enterAction/'

  void doTest() throws Throwable {
    final List<String> data = TestUtils.readInput(testDataPath + getTestName(true) + ".test");
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, data.get(0));
    doEnter();
    myFixture.checkResult(data.get(1));
  }

  private def doEnter() {
    myFixture.type('\n' as char)
  }

  public void testClos1() throws Throwable { doTest(); }
  public void testClos2() throws Throwable { doTest(); }
  public void testComment1() throws Throwable { doTest(); }
  public void testComment2() throws Throwable { doTest(); }
  public void testComment3() throws Throwable { doTest(); }
  public void testComment4() throws Throwable { doTest(); }
  public void testDef() throws Throwable { doTest(); }
  public void testDef2() throws Throwable { doTest(); }
  public void testGdoc1() throws Throwable { doTest(); }
  public void testGdoc2() throws Throwable { doTest(); }
  public void testGdoc3() throws Throwable { doTest(); }
  public void testGdoc4() throws Throwable { doTest(); }
  public void testGdoc5() throws Throwable { doTest(); }
  public void testGdoc6() throws Throwable { doTest(); }
  public void testGdoc7() throws Throwable { doTest(); }
  public void testGdoc8() throws Throwable { doTest(); }
  public void testGdoc9() throws Throwable { doTest(); }
  public void testGdoc10() throws Throwable { doTest(); }
  public void testGdoc11() throws Throwable { doTest(); }
  public void testBuildGdocPrecededByGNewLine() throws Throwable { doTest(); }
  public void testGRVY_953() throws Throwable { doTest(); }
  public void testGstring1() throws Throwable { doTest(); }
  public void testGstring10() throws Throwable { doTest(); }
  public void testGstring11() throws Throwable { doTest(); }
  public void testGstring12() throws Throwable { doTest(); }
  public void testGstring13() throws Throwable { doTest(); }
  public void testGstring14() throws Throwable { doTest(); }
  public void testGstring15() throws Throwable { doTest(); }
  public void testGstring16() throws Throwable { doTest(); }
  public void testGstring2() throws Throwable { doTest(); }
  public void testGstring3() throws Throwable { doTest(); }
  public void testGstring4() throws Throwable { doTest(); }
  public void testGstring5() throws Throwable { doTest(); }
  public void testGstring6() throws Throwable { doTest(); }
  public void testGstring7() throws Throwable { doTest(); }
  public void testGstring8() throws Throwable { doTest(); }
  public void testGstring9() throws Throwable { doTest(); }
  public void testMultilineIndent() throws Throwable { doTest(); }
  public void testMultilineIndent2() throws Throwable { doTest(); }
  public void testSpaces1() throws Throwable { doTest(); }
  public void testString1() throws Throwable { doTest(); }
  public void testString2() throws Throwable { doTest(); }
  public void testString3() throws Throwable { doTest(); }
  public void testString4() throws Throwable { doTest(); }
  public void testString5() throws Throwable { doTest(); }
  public void testString6() throws Throwable { doTest(); }
  public void testString7() throws Throwable { doTest(); }
  public void testString8() throws Throwable { doTest(); }
  public void testString9() throws Throwable { doTest(); }
  public void testString10() throws Throwable { doTest(); }
  public void testRegex1() {doTest()}
  public void testRegex2() {doTest()}
  public void testRegex3() {doTest()}
  public void testRegex4() {doTest()}
  public void testRegex5() {doTest()}
  public void testRegex6() {doTest()}
  public void testRegex7() {doTest()}
  public void testRegex8() {doTest()}
  public void testRegex9() {doTest()}
  public void testRegex10() {doTest()}

  def doTest(String before, String after) {
    myFixture.configureByText("a.groovy", before)
    doEnter()
    myFixture.checkResult(after, true)
  }

  public void testAfterClosureArrow() throws Throwable {
    doTest """
def c = { a -><caret> }
""", """
def c = { a ->
  <caret>
}
"""
  }
  public void testAfterClosureArrowWithBody() throws Throwable {
    doTest """
def c = { a -><caret> zzz }
""", """
def c = { a ->
<caret>  zzz }
"""
  }
  public void testAfterClosureArrowWithBody2() throws Throwable {
    doTest """
def c = { a -> <caret>zzz }
""", """
def c = { a ->
  <caret>zzz }
"""
  }

  public void testBeforeClosingClosureBrace() throws Throwable {
    doTest """
def c = { a ->
  zzz <caret>}
""", """
def c = { a ->
  zzz 
<caret>}
"""
  }

  public void testAfterCase() {
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

  public void testCaseBeforeReturn() {
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

  public void testCaseAfterBreak() {
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

  public void testCaseAfterCall() {
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

  public void testCaseAfterReturn() {
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

  public void testAlmostBeforeClosingClosureBrace() throws Throwable {
    doTest  """
def c = { a ->
  zzz<caret> }
""", """
def c = { a ->
  zzz
<caret>}
"""
  }

  public void testEnterWithAlignedParameters() {
    groovySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    doTest """foo(2,<caret>)
""", """foo(2,
    <caret>)
"""

  }

  public void testEnterWithAlignedParameters2() {
      groovySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    doTest """foo(2,<caret>
""", """foo(2,
    <caret>
"""

  }

  public void testEnterAfterAssignmentInDeclaration() {
    doTest """def greeting = <caret>
""", """def greeting =
    <caret>
"""
  }

  public void testEnterInAssignment() {
    doTest """greeting = <caret>
""", """greeting =
    <caret>
"""

  }
  public void testEnterInIf() {
    doTest """if (2==2) <caret>
""", """if (2==2)
  <caret>
"""

  }

  public void testGeese1() {
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

  public void testGeese2() {
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

  public void "test enter after doc with wrong braces"() {
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
}

