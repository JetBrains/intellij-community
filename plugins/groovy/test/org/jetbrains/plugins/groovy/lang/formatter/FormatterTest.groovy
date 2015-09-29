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

package org.jetbrains.plugins.groovy.lang.formatter

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * Test suite for static formatting. Compares two files:
 * before and after formatting
 *
 * @author Ilya.Sergey
 */
public class FormatterTest extends GroovyFormatterTestCase {

  final String basePath = TestUtils.testDataPath + "groovy/formatter/"

  public void testAddign1() throws Throwable { doTest(); }
  public void testArg1() throws Throwable { doTest(); }
  public void testArg2() throws Throwable { doTest(); }
  public void testBin1() throws Throwable { doTest(); }
  public void testBin2() throws Throwable { doTest(); }
  public void testBin3() throws Throwable { doTest(); }
  public void testBlockExpr1() throws Throwable {
    //groovySettings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = false
    groovySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false
    doTest();
  }
  public void testClass1() throws Throwable { doTest(); }
  public void testClo1() throws Throwable { doTest(); }
  public void testClo2() throws Throwable { doTest(); }
  public void testClo3() throws Throwable { doTest(); }
  public void testClo4() throws Throwable { doTest(); }
  public void testColon1() throws Throwable { doTest(); }
  public void testColon2() throws Throwable { doTest(); }
  public void testCond1() throws Throwable { doTest(); }
  public void testDoc1() throws Throwable { doTest(); }
  public void testDoc2() throws Throwable { doTest(); }
  public void testDoc3() throws Throwable { doTest(); }
  public void testDockter() throws Throwable { doTest(); }
  public void testDot1() throws Throwable { doTest(); }
  public void testDot2() throws Throwable { doTest(); }
  public void testFor1() throws Throwable { doTest(); }
  public void testFor2() throws Throwable { doTest(); }
  public void testFor3() throws Throwable { doTest(); }
  public void testFor4() throws Throwable { doTest(); }
  public void testGbegin1() throws Throwable { doTest(); }
  public void testGrvy1637() throws Throwable { doTest(); }
  public void testGString1() throws Throwable { doTest(); }
  public void testMap6() throws Throwable { doTest(); }
  public void testMeth1() throws Throwable { doTest(); }
  public void testMeth2() throws Throwable { doTest(); }
  public void testMeth3() throws Throwable { doTest(); }
  public void testMeth4() throws Throwable { doTest(); }
  public void testMeth5() throws Throwable { doTest(); }
  public void testMultistring1() throws Throwable { doTest(); }
  public void testMultistring2() throws Throwable { doTest(); }
  public void testNew1() throws Throwable { doTest(); }
  public void testParam1() throws Throwable { doTest(); }
  public void testParam2() throws Throwable { doTest(); }
  public void testParen1() throws Throwable { doTest(); }
  public void testPath1() throws Throwable { doTest(); }
  public void testPointer1() throws Throwable { doTest(); }
  public void testRange1() throws Throwable { doTest(); }
  public void testRegex1() throws Throwable { doTest(); }
  public void testRegex2() { doTest() }
  public void testSh1() throws Throwable { doTest(); }
  public void testSh2() throws Throwable { doTest(); }
  public void testSqr1() throws Throwable { doTest(); }
  public void testSqr2() throws Throwable { doTest(); }
  public void testSqr3() throws Throwable { doTest(); }
  public void testString1() throws Throwable { doTest(); }
  public void testSuper1() throws Throwable { doTest(); }
  public void testSwitch1() throws Throwable { doTest(); }
  public void testSwitch2() throws Throwable { doTest(); }
  public void testSwitch3() throws Throwable { doTest(); }
  public void testSwitch4() throws Throwable { doTest(); }
  public void testSwitch5() throws Throwable { doTest(); }
  public void testSwitch6() throws Throwable { doTest(); }
  public void testSwitch7() throws Throwable { doTest(); }
  public void testSwitch8() throws Throwable { doTest(); }
  public void testType1() throws Throwable { doTest(); }
  public void testTypeparam1() throws Throwable { doTest(); }
  public void testUn1() throws Throwable { doTest(); }
  public void testUn2() throws Throwable { doTest(); }
  public void testUn3() throws Throwable { doTest(); }
  public void testWhile1() throws Throwable { doTest(); }

  public void testWhile2() throws Throwable { doTest(); }

  public void testWhileCStyle() throws Throwable { doTest(); }
  public void testFields() throws Throwable { doTest(); }

  public void testClosureAfterLineComment() throws Throwable {
    groovySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false
    doTest();
  }
  public void testAnnotationOnSeparateLine() throws Throwable { doTest(); }
  public void testAlignMultipleVariables() throws Throwable { doTest(); }

  public void testSpockTableWithStringComment() throws Throwable { doTest() }
  public void testSpockTableWithComments() throws Throwable { doTest() }

  public void testElseIfs() throws Throwable {
    groovySettings.SPECIAL_ELSE_IF_TREATMENT = false;
    doTest();
  }

  public void testElseIfsSpecial() throws Throwable { doTest(); }
  public void testVarargDeclaration() throws Throwable { doTest(); }
  public void testPreserveSpaceBeforeClosureParameters() throws Throwable { doTest(); }
  public void testPreserveGroovydoc() throws Throwable { doTest(); }

  public void testCaseInSwitch() throws Throwable {
    groovySettings.INDENT_CASE_FROM_SWITCH = false;
    doTest();
  }
  public void testCaseInSwitchIndented() throws Throwable { doTest(); }

  public void testStuffAfterLineComments() throws Throwable { doTest(); }

  public void testAnonymousInCall() throws Throwable {
    groovySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest();
  }

  public void _testLabelIndent() throws Throwable {
    groovySettings.indentOptions.LABEL_INDENT_SIZE = -2;
    doTest();
  }

  public void _testLabelIndentAbsolute() throws Throwable {
    groovySettings.indentOptions.LABEL_INDENT_ABSOLUTE = true;
    groovySettings.indentOptions.LABEL_INDENT_SIZE = 1;
    doTest();
  }

  public void testClosureParametersAligned() throws Throwable {
    groovySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest();
  }
  public void testAlignClosureBraceWithCall() throws Throwable { doTest(); }
  public void testFlyingGeese() throws Throwable {
    myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class).USE_FLYING_GEESE_BRACES = true;
    doTest();
  }
  public void testSpaceAfterTypeCast() throws Throwable {
    groovySettings.SPACE_AFTER_TYPE_CAST = false;
    groovySettings.SPACE_WITHIN_CAST_PARENTHESES = true;
    doTest();
  }

  public void testChainCallWithClosures() throws Throwable { doTest(); }
  public void testFormatDiamonds() throws Throwable { doTest(); }
  public void testFormatAnonymousDiamonds() throws Throwable { doTest(); }
  public void testPreserveChainingLineBreaks() throws Throwable { doTest(); }
  public void testMultilineEolComment() throws Throwable { doTest(); }
  public void testRedundantClosureSpace() throws Throwable { doTest(); }
  public void testIndentNamedArguments() throws Throwable { doTest(); }
  public void testIndentAssigned() throws Throwable { doTest(); }
  public void testCommentBeforeMultilineString() throws Throwable { doTest(); }
  public void testMethodSemicolons() throws Throwable { doTest(); }

  public void testNoFlyingGeese() throws Throwable {
    myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class).SPACE_IN_NAMED_ARGUMENT = false;
    myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class).USE_FLYING_GEESE_BRACES = false;
    doTest();
  }

  public void testAlignChainedCalls() throws Throwable {
    groovySettings.ALIGN_MULTILINE_CHAINED_METHODS = true;
    doTest();
  }

  public void testAlignBinaries() throws Throwable {
    groovySettings.ALIGN_MULTILINE_BINARY_OPERATION = true;
    doTest();
  }

  public void testAlignTernaries() throws Throwable {
    groovySettings.ALIGN_MULTILINE_TERNARY_OPERATION = true;
    doTest();
  }

  public void testAlignAssignments() throws Throwable {
    groovySettings.ALIGN_MULTILINE_ASSIGNMENT = true;
    doTest();
  }

  public void doTest() {
    def (String before, String after) = TestUtils.readInput(testDataPath + getTestName(true) + ".test");
    checkFormatting(before, StringUtil.trimEnd(after, "\n"));
  }

  public void testJavadocLink() throws Throwable {
    // Check that no unnecessary white spaces are introduced for the javadoc link element.
    // Check IDEA-57573 for more details.
    doTest();
  }

  public void testFieldInColumnsAlignment() {
    groovySettings.ALIGN_GROUP_FIELD_DECLARATIONS = true;
    groovySettings.FIELD_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    groovySettings.VARIABLE_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;

    doTest();
  }

  public void testGeese1() {doGeeseTest();}
  public void testGeese2() {doGeeseTest();}
  public void testGeese3() {doGeeseTest();}
  public void testGeese4() {doGeeseTest();}
  public void testGeese5() {doGeeseTest();}
  public void testGeese6() {doGeeseTest();}
  public void testGeese7() {doGeeseTest();}
  public void testGeese8() {doGeeseTest();}

  public void testMapInArgumentList() {doTest();}
  public void testMapInArgList2() {
    myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class).ALIGN_NAMED_ARGS_IN_MAP = true;
    doTest();
  }

  public void testForceBraces() {
    groovySettings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;
    groovySettings.FOR_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;
    groovySettings.WHILE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;
    doTest();
  }

  void testNonIndentAfterClosureQualifier() { doTest() }
  void testNonIndentAfterClosureQualifier2() { doTest() }
  void testNonIndentAfterClosureQualifier3() { doTest() }

  void testAssertDescriptionIndent() { doTest() }

  void testPackageDef1() { doTest() }
  void testPackageDef2() { doTest() }

  void testAnnotationArgs1() { doTest() }
  void testAnnotationArgs2() { doTest() }

  void testImplementsList() { doTest() }

  void testSimpleClassInOneLine() {
    groovySettings.KEEP_SIMPLE_CLASSES_IN_ONE_LINE = false
    checkFormatting('''\
class A {}
class B {
}
''', '''\
class A {
}

class B {
}
''')
  }

  void testSimpleMethodInOneLine() {
    groovySettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = false
    checkFormatting('''\
def foo() {2}
''', '''\
def foo() {
  2
}
''')

    groovySettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true
    checkFormatting('''\
def foo() {2}
''', '''\
def foo() { 2 }
''')

  }

  void testSimpleBlocksInOneLine() {
    groovySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true
    checkFormatting('''\
if (abc) {return 2}
''', '''\
if (abc) { return 2 }
''')

    groovySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false
    checkFormatting('''\
if (abc) {return 2}
''', '''\
if (abc) {
  return 2
}
''')
  }

  void testControlStatementsInOneLine() {
    groovySettings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = true
    checkFormatting('''\
if (abc) return 2
''', '''\
if (abc) return 2
''')
    checkFormatting('''\
while (abc) return 2
''', '''\
while (abc) return 2
''')
    checkFormatting('''\
for (abc) return 2
''', '''\
for (abc) return 2
''')

    groovySettings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = false
    checkFormatting('''\
if (abc) return 2
''', '''\
if (abc)
  return 2
''')
    checkFormatting('''\
while (abc) return 2
''', '''\
while (abc)
  return 2
''')

    checkFormatting('''\
for (;abc;) return 2
''', '''\
for (; abc;)
  return 2
''')

  }

  void testWrapThrows() {
    groovySettings.THROWS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS

    checkFormatting('''\
def foo() throws X {}
''', '''\
def foo()
    throws X {}
''')
  }

  void testSpacesWithinClosure0() {
    groovySettings.SPACE_WITHIN_BRACES = true
    checkFormatting('''def cl = {print 2}''', '''def cl = { print 2 }''')
  }

  void testSpacesWithinClosure1() {
    groovySettings.SPACE_WITHIN_BRACES = true
    checkFormatting('''\
def cl = {
print 2}
''', '''\
def cl = {
  print 2
}
''')
  }

  void testSpacesWithinClosure2() {
    groovySettings.SPACE_WITHIN_BRACES = true
    checkFormatting('''\
def cl = {->
print 2}
''', '''\
def cl = { ->
  print 2
}
''')
  }

  void testSpacesWithinClosure3() {
    groovySettings.SPACE_WITHIN_BRACES = true
    checkFormatting('''\
def cl = {def a->
print 2}
''', '''\
def cl = { def a ->
  print 2
}
''')
  }

  void testSpacesWithinClosure4() {
    groovySettings.SPACE_WITHIN_BRACES = true
    checkFormatting('''\
def cl = {
def a->
print 2}
''', '''\
def cl = {
  def a ->
    print 2
}
''')
  }

  void testSpacesWithinClosure5() {
    groovySettings.SPACE_WITHIN_BRACES = false
    checkFormatting('''def cl = { print 2 }''', '''def cl = {print 2}''')
  }

  void testSpacesWithinClosure6() {
    groovySettings.SPACE_WITHIN_BRACES = false
    checkFormatting('''\
def cl = {
print 2}
''', '''\
def cl = {
  print 2
}
''')
  }

  void testSpacesWithinClosure7() {
    groovySettings.SPACE_WITHIN_BRACES = false
    checkFormatting('''\
def cl = { ->
print 2}
''', '''\
def cl = {->
  print 2
}
''')
  }

  void testSpacesWithinClosure8() {
    groovySettings.SPACE_WITHIN_BRACES = false
    checkFormatting('''\
def cl = { def a->
print 2}
''', '''\
def cl = {def a ->
  print 2
}
''')
  }

  void testSpacesWithinClosure9() {
    groovySettings.SPACE_WITHIN_BRACES = false
    checkFormatting('''\
def cl = {
def a->
print 2}
''', '''\
def cl = {
  def a ->
    print 2
}
''')
  }

  void testLineFeedsInMethodParams0() {
    groovySettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true
    checkFormatting('''\
def foo(String s,
int x) {}
''', '''\
def foo(
    String s,
    int x) {}
''')
  }

  void testLineFeedsInMethodParams1() {
    groovySettings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true
    checkFormatting('''\
def foo(String s,
int x) {}
''', '''\
def foo(String s,
        int x
) {}
''')
  }

  void testLineFeedsInMethodParams2() {
    groovySettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true
    groovySettings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true
    checkFormatting('''\
def foo(String s,
int x) {}
''', '''\
def foo(
    String s,
    int x
) {}
''')
  }

  void testLineFeedsInMethodParams3() {
    groovySettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true
    groovySettings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true
    groovySettings.SPACE_WITHIN_METHOD_PARENTHESES = true
    checkFormatting('''\
def foo(String s, int x) {}
''', '''\
def foo( String s, int x ) {}
''')
  }

  void testLineFeedsInMethodCall0() {
    groovySettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true
    checkFormatting('''\
foo(s,
    x)
''', '''\
foo(
    s,
    x)
''')
  }

  void testLineFeedsInMethodCall1() {
    groovySettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true
    checkFormatting('''\
foo(s,
x)
''', '''\
foo(s,
    x
)
''')
  }

  void testLineFeedsInMethodCall2() {
    groovySettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true
    groovySettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true
    checkFormatting('''\
foo(s,
 x)
''', '''\
foo(
    s,
    x
)
''')
  }

  void testLineFeedsInMethodCall3() {
    groovySettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true
    groovySettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true
    groovySettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true
    checkFormatting('''\
foo(s, x)
''', '''\
foo( s, x )
''')
  }

  void testLineFeedsInMethodCall4() {
    groovySettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true
    groovySettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true
    groovySettings.SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES = true
    checkFormatting('''\
foo()
''', '''\
foo( )
''')
  }

  void testAlignMethodParentheses() {
    groovySettings.ALIGN_MULTILINE_METHOD_BRACKETS = true
    checkFormatting('''\
def foooo(
String s
) {}
''', '''\
def foooo(
    String s
         ) {}
''')
  }

  void testAlignFor() {
    groovySettings.ALIGN_MULTILINE_FOR = true
    checkFormatting('''\
for (int i = 3;
i<2;
i++) print 2
''', '''\
for (int i = 3;
     i < 2;
     i++) print 2
''')
  }

  void testBinaryOperationSingOnNewLine() {
    groovySettings.BINARY_OPERATION_SIGN_ON_NEXT_LINE = true

    checkFormatting('''\
(1 +
 2) + 3
''', '''\
(1
    +
    2)
    + 3
''')
  }

  void testParenthesized0() {
    groovySettings.PARENTHESES_EXPRESSION_LPAREN_WRAP = true
    checkFormatting('''\
(2+
3)
''', '''\
(
    2 +
        3)
''')
  }

  void testParenthesized1() {
    groovySettings.PARENTHESES_EXPRESSION_RPAREN_WRAP = true
    checkFormatting('''\
(2+
3)
''', '''\
(2 +
    3
)
''')
  }

  void testParenthesized2() {
    groovySettings.PARENTHESES_EXPRESSION_LPAREN_WRAP = true
    groovySettings.PARENTHESES_EXPRESSION_RPAREN_WRAP = true
    checkFormatting('''\
(2+
3)
''', '''\
(
    2 +
        3
)
''')
  }

  void testParenthesized3() {
    groovySettings.PARENTHESES_EXPRESSION_LPAREN_WRAP = true
    groovySettings.PARENTHESES_EXPRESSION_RPAREN_WRAP = true
    groovySettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    checkFormatting('''\
(2+
3)
''', '''\
(
    2 +
    3
)
''')
  }

  void testAlignBinaryOperands() {
    groovySettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    checkFormatting('''\
(2+
3 +
4)
''', '''\
(2 +
 3 +
 4)
''')
  }


  void testConditional0() {
    groovySettings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    checkFormatting '''\
print abc ?
cde:
xyz
''', '''\
print abc ?
      cde :
      xyz
'''
  }

  void testConditional1() {
    groovySettings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    checkFormatting '''\
print abc ?:
xyz
''', '''\
print abc ?:
      xyz
'''
  }

  void testConditional2() {
    groovySettings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    checkFormatting('''\
print abc ? cde
:xyz''', '''\
print abc ? cde
          : xyz''')
  }

  void testLabelsInBasicMode() {
    groovySettings.indentOptions.INDENT_SIZE = 4
    groovySettings.indentOptions.LABEL_INDENT_SIZE = -2
    groovyCustomSettings.INDENT_LABEL_BLOCKS = false

    checkFormatting('''\
def bar() {
  abc:
  foo()
  bar()
}
''', '''\
def bar() {
  abc:
    foo()
    bar()
}
''')
  }

  void testLabels() {
    groovyCustomSettings.INDENT_LABEL_BLOCKS = false
    checkFormatting('''\
def foo() {
abc:foo()
bar()
}
''', '''\
def foo() {
  abc: foo()
  bar()
}
''')
  }

  void testGdocAsterisks() {
    checkFormatting('''\
/*****
*
*****/
''', '''\
/*****
 *
 *****/
''')
  }

  void testInKeyword() {
    checkFormatting('foo in  bar', 'foo in bar')
  }

  void testGDocAfterImports() { doTest() }
  void testGroovyDocAfterImports2() { doTest() }

  void testRegexExpressions() { doTest() }

  void testSpreadArg() { doTest() }

  void testExtraLines() { doTest() }

  void testLabelWithDescription() {
    GroovyCodeStyleSettings customSettings = myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class)
    CommonCodeStyleSettings commonSettings = myTempSettings.getCommonSettings(GroovyLanguage.INSTANCE)

    boolean indentLabelBlocks = customSettings.INDENT_LABEL_BLOCKS
    int labelIndentSize = commonSettings.indentOptions.LABEL_INDENT_SIZE
    try {
      customSettings.INDENT_LABEL_BLOCKS = true
      commonSettings.indentOptions.LABEL_INDENT_SIZE = 2
      doTest()
    }
    finally {
      customSettings.INDENT_LABEL_BLOCKS = indentLabelBlocks
      commonSettings.indentOptions.LABEL_INDENT_SIZE = labelIndentSize
    }
  }

  void testNoLineFeedsInGString() { doTest() }

  private void doGeeseTest() {
    GroovyCodeStyleSettings customSettings = myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class)
    boolean oldvalue = customSettings.USE_FLYING_GEESE_BRACES
    try {
      customSettings.USE_FLYING_GEESE_BRACES = true
      doTest()
    }
    finally {
      customSettings.USE_FLYING_GEESE_BRACES = oldvalue
    }
  }
}
