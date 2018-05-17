// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.formatter

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * Test suite for static formatting. Compares two files:
 * before and after formatting
 *
 * @author Ilya.Sergey
 */
class FormatterTest extends GroovyFormatterTestCase {

  final String basePath = TestUtils.testDataPath + "groovy/formatter/"

  void testAddign1() throws Throwable { doTest() }

  void testArg1() throws Throwable { doTest() }

  void testArg2() throws Throwable { doTest() }

  void testBin1() throws Throwable { doTest() }

  void testBin2() throws Throwable { doTest() }

  void testBin3() throws Throwable { doTest() }

  void testBlockExpr1() throws Throwable {
    //groovySettings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = false
    groovySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false
    doTest()
  }

  void testClass1() throws Throwable { doTest() }

  void testClo1() throws Throwable { doTest() }

  void testClo2() throws Throwable { doTest() }

  void testClo3() throws Throwable { doTest() }

  void testClo4() throws Throwable { doTest() }

  void testColon1() throws Throwable { doTest() }

  void testColon2() throws Throwable { doTest() }

  void testCond1() throws Throwable { doTest() }

  void testDoc1() throws Throwable { doTest() }

  void testDoc2() throws Throwable { doTest() }

  void testDoc3() throws Throwable { doTest() }

  void testDockter() throws Throwable { doTest() }

  void testDot1() throws Throwable { doTest() }

  void testDot2() throws Throwable { doTest() }

  void testFor1() throws Throwable { doTest() }

  void testFor2() throws Throwable { doTest() }

  void testFor3() throws Throwable { doTest() }

  void testFor4() throws Throwable { doTest() }

  void testGbegin1() throws Throwable { doTest() }

  void testGrvy1637() throws Throwable { doTest() }

  void testGString1() throws Throwable { doTest() }

  void testMap6() throws Throwable { doTest() }

  void testMeth1() throws Throwable { doTest() }

  void testMeth2() throws Throwable { doTest() }

  void testMeth3() throws Throwable { doTest() }

  void testMeth4() throws Throwable { doTest() }

  void testMeth5() throws Throwable { doTest() }

  void testMultistring1() throws Throwable { doTest() }

  void testMultistring2() throws Throwable { doTest() }

  void testNew1() throws Throwable { doTest() }

  void testParam1() throws Throwable { doTest() }

  void testParam2() throws Throwable { doTest() }

  void testParen1() throws Throwable { doTest() }

  void testPath1() throws Throwable { doTest() }

  void testPointer1() throws Throwable { doTest() }

  void testRange1() throws Throwable { doTest() }

  void testRegex1() throws Throwable { doTest() }

  void testRegex2() { doTest() }

  void testSh1() throws Throwable { doTest() }

  void testSh2() throws Throwable { doTest() }

  void testSqr1() throws Throwable { doTest() }

  void testSqr2() throws Throwable { doTest() }

  void testSqr3() throws Throwable { doTest() }

  void testString1() throws Throwable { doTest() }

  void testSuper1() throws Throwable { doTest() }

  void testSwitch1() throws Throwable { doTest() }

  void testSwitch2() throws Throwable { doTest() }

  void testSwitch3() throws Throwable { doTest() }

  void testSwitch4() throws Throwable { doTest() }

  void testSwitch5() throws Throwable { doTest() }

  void testSwitch6() throws Throwable { doTest() }

  void testSwitch7() throws Throwable { doTest() }

  void testSwitch8() throws Throwable { doTest() }

  void testType1() throws Throwable { doTest() }

  void testTypeparam1() throws Throwable { doTest() }

  void testUn1() throws Throwable { doTest() }

  void testUn2() throws Throwable { doTest() }

  void testUn3() throws Throwable { doTest() }

  void testWhile1() throws Throwable { doTest() }

  void testWhile2() throws Throwable { doTest() }

  void testWhileCStyle() throws Throwable { doTest() }

  void testFields() throws Throwable { doTest() }

  void testClosureAfterLineComment() throws Throwable {
    groovySettings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false
    doTest()
  }

  void testAnnotationOnSeparateLine() throws Throwable { doTest() }

  void testAlignMultipleVariables() throws Throwable { doTest() }

  void testSpockTableWithStringComment() throws Throwable { doTest() }

  void testSpockTableWithComments() throws Throwable { doTest() }

  void testElseIfs() throws Throwable {
    groovySettings.SPECIAL_ELSE_IF_TREATMENT = false
    doTest()
  }

  void testElseIfsSpecial() throws Throwable { doTest() }

  void testVarargDeclaration() throws Throwable { doTest() }

  void testPreserveSpaceBeforeClosureParameters() throws Throwable { doTest() }

  void testPreserveGroovydoc() throws Throwable { doTest() }

  void testCaseInSwitch() throws Throwable {
    groovySettings.INDENT_CASE_FROM_SWITCH = false
    doTest()
  }

  void testCaseInSwitchIndented() throws Throwable { doTest() }

  void testStuffAfterLineComments() throws Throwable { doTest() }

  void testAnonymousInCall() throws Throwable {
    groovySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    doTest()
  }

  void _testLabelIndent() throws Throwable {
    groovySettings.indentOptions.LABEL_INDENT_SIZE = -2
    doTest()
  }

  void _testLabelIndentAbsolute() throws Throwable {
    groovySettings.indentOptions.LABEL_INDENT_ABSOLUTE = true
    groovySettings.indentOptions.LABEL_INDENT_SIZE = 1
    doTest()
  }

  void testClosureParametersAligned() throws Throwable {
    groovySettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true
    doTest()
  }

  void testAlignClosureBraceWithCall() throws Throwable { doTest() }

  void testFlyingGeese() throws Throwable {
    myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class).USE_FLYING_GEESE_BRACES = true
    doTest()
  }

  void testSpaceAfterTypeCast() throws Throwable {
    groovySettings.SPACE_AFTER_TYPE_CAST = false
    groovySettings.SPACE_WITHIN_CAST_PARENTHESES = true
    doTest()
  }

  void testChainCallWithClosures() throws Throwable { doTest() }

  void testFormatDiamonds() throws Throwable { doTest() }

  void testFormatAnonymousDiamonds() throws Throwable { doTest() }

  void testPreserveChainingLineBreaks() throws Throwable { doTest() }

  void testMultilineEolComment() throws Throwable { doTest() }

  void testRedundantClosureSpace() throws Throwable { doTest() }

  void testIndentNamedArguments() throws Throwable { doTest() }

  void testIndentAssigned() throws Throwable { doTest() }

  void testCommentBeforeMultilineString() throws Throwable { doTest() }

  void testMethodSemicolons() throws Throwable { doTest() }

  void testNoFlyingGeese() throws Throwable {
    myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class).SPACE_IN_NAMED_ARGUMENT = false
    myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class).USE_FLYING_GEESE_BRACES = false
    doTest()
  }

  void testAlignChainedCalls() throws Throwable {
    groovySettings.ALIGN_MULTILINE_CHAINED_METHODS = true
    doTest()
  }

  void testAlignBinaries() throws Throwable {
    groovySettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    doTest()
  }

  void testAlignTernaries() throws Throwable {
    groovySettings.ALIGN_MULTILINE_TERNARY_OPERATION = true
    doTest()
  }

  void testAlignAssignments() throws Throwable {
    groovySettings.ALIGN_MULTILINE_ASSIGNMENT = true
    doTest()
  }

  void doTest() {
    def (String before, String after) = TestUtils.readInput(testDataPath + getTestName(true) + ".test")
    checkFormatting(before, StringUtil.trimEnd(after, "\n"))
  }

  void testJavadocLink() throws Throwable {
    // Check that no unnecessary white spaces are introduced for the javadoc link element.
    // Check IDEA-57573 for more details.
    doTest()
  }

  void testFieldInColumnsAlignment() {
    groovySettings.ALIGN_GROUP_FIELD_DECLARATIONS = true
    groovySettings.FIELD_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    groovySettings.VARIABLE_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP

    doTest()
  }

  void testGeese1() { doGeeseTest() }

  void testGeese2() { doGeeseTest() }

  void testGeese3() { doGeeseTest() }

  void testGeese4() { doGeeseTest() }

  void testGeese5() { doGeeseTest() }

  void testGeese6() { doGeeseTest() }

  void testGeese7() { doGeeseTest() }

  void testGeese8() { doGeeseTest() }

  void testMapInArgumentList() { doTest() }

  void testMapInArgList2() {
    myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class).ALIGN_NAMED_ARGS_IN_MAP = true
    doTest()
  }

  void testForceBraces() {
    groovySettings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE
    groovySettings.FOR_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE
    groovySettings.WHILE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE
    doTest()
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
for (abc in abc) return 2
''', '''\
for (abc in abc) return 2
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

  void testEnumAnnotations() {
    checkFormatting('''\
enum GroovyEnum {
  FOO,
  @Deprecated
          BAR(""),
  DAR
}
''', '''\
enum GroovyEnum {
  FOO,
  @Deprecated
  BAR(""),
  DAR
}
''')
  }

  void testEnumAnnotationsSingleLine() {
    checkFormatting('''\
enum GroovyEnum {
  @Deprecated    BAR("")
}
''', '''\
enum GroovyEnum {
  @Deprecated BAR("")
}
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
