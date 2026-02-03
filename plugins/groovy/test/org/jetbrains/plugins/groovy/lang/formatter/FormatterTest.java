// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.formatter;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.Iterator;

public class FormatterTest extends GroovyFormatterTestCase {
  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/formatter/";
  }

  public void testAdding1() { doTest(); }

  public void testArg1() { doTest(); }

  public void testArg2() { doTest(); }

  public void testBin1() { doTest(); }

  public void testBin2() { doTest(); }

  public void testBin3() { doTest(); }

  public void testBlockExpr1() {
    //groovySettings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = false
    getGroovySettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
    doTest();
  }

  public void testClass1() { doTest(); }

  public void testClo1() { doTest(); }

  public void testClo2() { doTest(); }

  public void testClo3() { doTest(); }

  public void testClo4() { doTest(); }

  public void testColon1() { doTest(); }

  public void testColon2() { doTest(); }

  public void testCond1() { doTest(); }

  public void testDoc1() { doTest(); }

  public void testDoc2() { doTest(); }

  public void testDoc3() { doTest(); }

  public void testInlineTag() { doTest(); }

  public void testDockter() { doTest(); }

  public void testDot1() { doTest(); }

  public void testDot2() { doTest(); }

  public void testFor1() { doTest(); }

  public void testFor2() { doTest(); }

  public void testFor3() { doTest(); }

  public void testFor4() { doTest(); }

  public void testGbegin1() { doTest(); }

  public void testGrvy1637() { doTest(); }

  public void testGString1() { doTest(); }

  public void testMap6() { doTest(); }

  public void testMeth1() { doTest(); }

  public void testMeth2() { doTest(); }

  public void testMeth3() { doTest(); }

  public void testMeth4() { doTest(); }

  public void testMeth5() { doTest(); }

  public void testMultistring1() { doTest(); }

  public void testMultistring2() { doTest(); }

  public void testNew1() { doTest(); }

  public void testParam1() { doTest(); }

  public void testParam2() { doTest(); }

  public void testParen1() { doTest(); }

  public void testPath1() { doTest(); }

  public void testPointer1() { doTest(); }

  public void testRange1() { doTest(); }

  public void testRegex1() { doTest(); }

  public void testRegex2() { doTest(); }

  public void testSh1() { doTest(); }

  public void testSh2() { doTest(); }

  public void testSqr1() { doTest(); }

  public void testSqr2() { doTest(); }

  public void testSqr3() { doTest(); }

  public void testString1() { doTest(); }

  public void testSuper1() { doTest(); }

  public void testSwitch1() { doTest(); }

  public void testSwitch2() { doTest(); }

  public void testSwitch3() { doTest(); }

  public void testSwitch4() { doTest(); }

  public void testSwitch5() { doTest(); }

  public void testSwitch6() { doTest(); }

  public void testSwitch7() { doTest(); }

  public void testSwitch8() { doTest(); }

  public void testSwitchexpr1() { doTest(); }

  public void testSwitchexpr2() { doTest(); }

  public void testSwitchexpr3() { doTest(); }

  public void testSwitchexpr4() { doTest(); }

  public void testSwitchexpr5() { doTest(); }

  public void testSwitchexpr6() { doTest(); }

  public void testSwitchexpr7() { doTest(); }

  public void testType1() { doTest(); }

  public void testTypeparam1() { doTest(); }

  public void testUn1() { doTest(); }

  public void testUn2() { doTest(); }

  public void testUn3() { doTest(); }

  public void testWhile1() { doTest(); }

  public void testWhile2() { doTest(); }

  public void testWhileCStyle() { doTest(); }

  public void testFields() { doTest(); }

  public void testClosureAfterLineComment() {
    getGroovySettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
    doTest();
  }

  public void testAnnotationOnSeparateLine() { doTest(); }

  public void testAlignMultipleVariables() { doTest(); }

  public void testAlignMultipleVariablesLabeled() { doTest(); }

  public void testElseIfs() {
    getGroovySettings().SPECIAL_ELSE_IF_TREATMENT = false;
    doTest();
  }

  public void testElseIfsSpecial() { doTest(); }

  public void testVarargDeclaration() { doTest(); }

  public void testPreserveSpaceBeforeClosureParameters() { doTest(); }

  public void testPreserveGroovydoc() { doTest(); }

  public void testCaseInSwitch() {
    getGroovySettings().INDENT_CASE_FROM_SWITCH = false;
    doTest();
  }

  public void testCaseInSwitchIndented() { doTest(); }

  public void testStuffAfterLineComments() { doTest(); }

  public void testAnonymousInCall() {
    getGroovySettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest();
  }

  public void _testLabelIndent() {
    getGroovySettings().getIndentOptions().LABEL_INDENT_SIZE = -2;
    doTest();
  }

  public void testClosureParametersAligned() {
    getGroovySettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest();
  }

  public void testAlignClosureBraceWithCall() { doTest(); }

  public void testFlyingGeese() {
    myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class).USE_FLYING_GEESE_BRACES = true;
    doTest();
  }

  public void testSpaceAfterTypeCast() {
    getGroovySettings().SPACE_AFTER_TYPE_CAST = false;
    getGroovySettings().SPACE_WITHIN_CAST_PARENTHESES = true;
    doTest();
  }

  public void testChainCallWithClosures() { doTest(); }

  public void testFormatDiamonds() { doTest(); }

  public void testFormatAnonymousDiamonds() { doTest(); }

  public void testPreserveChainingLineBreaks() { doTest(); }

  public void testMultilineEolComment() { doTest(); }

  public void testRedundantClosureSpace() { doTest(); }

  public void testIndentNamedArguments() { doTest(); }

  public void testIndentAssigned() { doTest(); }

  public void testCommentBeforeMultilineString() { doTest(); }

  public void testMethodSemicolons() { doTest(); }

  public void testNoFlyingGeese() {
    myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class).SPACE_IN_NAMED_ARGUMENT = false;
    myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class).USE_FLYING_GEESE_BRACES = false;
    doTest();
  }

  public void testAlignChainedCalls() {
    getGroovySettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    doTest();
  }

  public void testAlignBinaries() {
    getGroovySettings().ALIGN_MULTILINE_BINARY_OPERATION = true;
    doTest();
  }

  public void testAlignTernaries() {
    getGroovySettings().ALIGN_MULTILINE_TERNARY_OPERATION = true;
    doTest();
  }

  public void testAlignAssignments() {
    getGroovySettings().ALIGN_MULTILINE_ASSIGNMENT = true;
    doTest();
  }

  public void doTest() {
    final Iterator<String> iterator = TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test").iterator();
    String before = iterator.hasNext() ? iterator.next() : null;
    String after = iterator.hasNext() ? iterator.next() : null;

    checkFormatting(before, StringUtil.trimEnd(after, "\n"));
  }

  public void testJavadocLink() {
    // Check that no unnecessary white spaces are introduced for the javadoc link element.
    // Check IDEA-57573 for more details.
    doTest();
  }

  public void testFieldInColumnsAlignment() {
    getGroovySettings().ALIGN_GROUP_FIELD_DECLARATIONS = true;
    getGroovySettings().FIELD_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    getGroovySettings().VARIABLE_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;

    doTest();
  }

  public void testGeese1() { doGeeseTest(); }

  public void testGeese2() { doGeeseTest(); }

  public void testGeese3() { doGeeseTest(); }

  public void testGeese4() { doGeeseTest(); }

  public void testGeese5() { doGeeseTest(); }

  public void testGeese6() { doGeeseTest(); }

  public void testGeese7() { doGeeseTest(); }

  public void testGeese8() { doGeeseTest(); }

  public void testMapInArgumentList() { doTest(); }

  public void testMapInArgList2() {
    myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class).ALIGN_NAMED_ARGS_IN_MAP = true;
    doTest();
  }

  public void testForceBraces() {
    getGroovySettings().IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;
    getGroovySettings().FOR_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;
    getGroovySettings().WHILE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;
    doTest();
  }

  public void testIndentAfterClosureQualifier() { doTest(); }

  public void testIndentAfterClosureQualifier2() { doTest(); }

  public void testIndentAfterClosureQualifier3() { doTest(); }

  public void testChainCallFieldIndent() {
    getGroovySettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    doTest();
  }

  public void testChainCallFieldIndent2() {
    getGroovySettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    doTest();
  }

  public void testChainCallFieldIndent3() {
    getGroovySettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    doTest();
  }

  public void testAssertDescriptionIndent() { doTest(); }

  public void testPackageDef1() { doTest(); }

  public void testPackageDef2() { doTest(); }

  public void testAnnotationArgs1() { doTest(); }

  public void testAnnotationArgs2() { doTest(); }

  public void testImplementsList() { doTest(); }

  public void testSimpleClassInOneLine() {
    getGroovySettings().KEEP_SIMPLE_CLASSES_IN_ONE_LINE = false;
    checkFormatting("""
                      class A {}
                      class B {
                      }
                      """, """
                      class A {
                      }
                      
                      class B {
                      }
                      """);
  }

  public void testSimpleMethodInOneLine() {
    getGroovySettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = false;
    checkFormatting("""
                      def foo() {2}
                      """, """
                      def foo() {
                        2
                      }
                      """);

    getGroovySettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    checkFormatting("""
                      def foo() {2}
                      """, """
                      def foo() { 2 }
                      """);
  }

  public void testSimpleBlocksInOneLine() {
    getGroovySettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    checkFormatting("""
                      if (abc) {return 2}
                      """, """
                      if (abc) { return 2 }
                      """);

    getGroovySettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
    checkFormatting("""
                      if (abc) {return 2}
                      """, """
                      if (abc) {
                        return 2
                      }
                      """);
  }

  public void testControlStatementsInOneLine() {
    getGroovySettings().KEEP_CONTROL_STATEMENT_IN_ONE_LINE = true;
    checkFormatting("""
                      if (abc) return 2
                      """, """
                      if (abc) return 2
                      """);
    checkFormatting("""
                      while (abc) return 2
                      """, """
                      while (abc) return 2
                      """);
    checkFormatting("""
                      for (abc in abc) return 2
                      """, """
                      for (abc in abc) return 2
                      """);

    getGroovySettings().KEEP_CONTROL_STATEMENT_IN_ONE_LINE = false;
    checkFormatting("""
                      if (abc) return 2
                      """, """
                      if (abc)
                        return 2
                      """);
    checkFormatting("""
                      while (abc) return 2
                      """, """
                      while (abc)
                        return 2
                      """);

    checkFormatting("""
                      for (;abc;) return 2
                      """, """
                      for (; abc;)
                        return 2
                      """);
  }

  public void testWrapThrows() {
    getGroovySettings().THROWS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

    checkFormatting("""
                      def foo() throws X {}
                      """, """
                      def foo()
                          throws X {}
                      """);
  }

  public void testSpacesWithinClosure0() {
    getGroovySettings().SPACE_WITHIN_BRACES = true;
    checkFormatting("""
                      def cl = {print 2}""", """
                      def cl = { print 2 }""");
  }

  public void testSpacesWithinClosure1() {
    getGroovySettings().SPACE_WITHIN_BRACES = true;
    checkFormatting("""
                      def cl = {
                      print 2}
                      """, """
                      def cl = {
                        print 2
                      }
                      """);
  }

  public void testSpacesWithinClosure2() {
    getGroovySettings().SPACE_WITHIN_BRACES = true;
    checkFormatting("""
                      def cl = {->
                      print 2}
                      """, """
                      def cl = { ->
                        print 2
                      }
                      """);
  }

  public void testSpacesWithinClosure3() {
    getGroovySettings().SPACE_WITHIN_BRACES = true;
    checkFormatting("""
                      def cl = {def a->
                      print 2}
                      """, """
                      def cl = { def a ->
                        print 2
                      }
                      """);
  }

  public void testSpacesWithinClosure4() {
    getGroovySettings().SPACE_WITHIN_BRACES = true;
    checkFormatting("""
                      def cl = {
                      def a->
                      print 2}
                      """, """
                      def cl = {
                        def a ->
                          print 2
                      }
                      """);
  }

  public void testSpacesWithinClosure5() {
    getGroovySettings().SPACE_WITHIN_BRACES = false;
    checkFormatting("""
                      def cl = { print 2 }""", """
                      def cl = {print 2}""");
  }

  public void testSpacesWithinClosure6() {
    getGroovySettings().SPACE_WITHIN_BRACES = false;
    checkFormatting("""
                      def cl = {
                      print 2}
                      """, """
                      def cl = {
                        print 2
                      }
                      """);
  }

  public void testSpacesWithinClosure7() {
    getGroovySettings().SPACE_WITHIN_BRACES = false;
    checkFormatting("""
                      def cl = { ->
                      print 2}
                      """, """
                      def cl = {->
                        print 2
                      }
                      """);
  }

  public void testSpacesWithinClosure8() {
    getGroovySettings().SPACE_WITHIN_BRACES = false;
    checkFormatting("""
                      def cl = { def a->
                      print 2}
                      """, """
                      def cl = {def a ->
                        print 2
                      }
                      """);
  }

  public void testSpacesWithinClosure9() {
    getGroovySettings().SPACE_WITHIN_BRACES = false;
    checkFormatting("""
                      def cl = {
                      def a->
                      print 2}
                      """, """
                      def cl = {
                        def a ->
                          print 2
                      }
                      """);
  }

  public void testLineFeedsInMethodParams0() {
    getGroovySettings().METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    checkFormatting("""
                      def foo(String s,
                      int x) {}
                      """, """
                      def foo(
                          String s,
                          int x) {}
                      """);
  }

  public void testLineFeedsInMethodParams1() {
    getGroovySettings().METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    checkFormatting("""
                      def foo(String s,
                      int x) {}
                      """, """
                      def foo(String s,
                              int x
                      ) {}
                      """);
  }

  public void testLineFeedsInMethodParams2() {
    getGroovySettings().METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    getGroovySettings().METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    checkFormatting("""
                      def foo(String s,
                      int x) {}
                      """, """
                      def foo(
                          String s,
                          int x
                      ) {}
                      """);
  }

  public void testLineFeedsInMethodParams3() {
    getGroovySettings().METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    getGroovySettings().METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    getGroovySettings().SPACE_WITHIN_METHOD_PARENTHESES = true;
    checkFormatting("""
                      def foo(String s, int x) {}
                      """, """
                      def foo( String s, int x ) {}
                      """);
  }

  public void testLineFeedsInMethodCall0() {
    getGroovySettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    checkFormatting("""
                      foo(s,
                          x)
                      """, """
                      foo(
                          s,
                          x)
                      """);
  }

  public void testLineFeedsInMethodCall1() {
    getGroovySettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    checkFormatting("""
                      foo(s,
                      x)
                      """, """
                      foo(s,
                          x
                      )
                      """);
  }

  public void testLineFeedsInMethodCall2() {
    getGroovySettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    getGroovySettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    checkFormatting("""
                      foo(s,
                       x)
                      """, """
                      foo(
                          s,
                          x
                      )
                      """);
  }

  public void testLineFeedsInMethodCall3() {
    getGroovySettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    getGroovySettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    getGroovySettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    checkFormatting("""
                      foo(s, x)
                      """, """
                      foo( s, x )
                      """);
  }

  public void testLineFeedsInMethodCall4() {
    getGroovySettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    getGroovySettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    getGroovySettings().SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES = true;
    checkFormatting("""
                      foo()
                      """, """
                      foo( )
                      """);
  }

  public void testAlignMethodParentheses() {
    getGroovySettings().ALIGN_MULTILINE_METHOD_BRACKETS = true;
    checkFormatting("""
                      def foooo(
                      String s
                      ) {}
                      """, """
                      def foooo(
                          String s
                               ) {}
                      """);
  }

  public void testEnumAnnotations() {
    checkFormatting("""
                      enum GroovyEnum {
                        FOO,
                        @Deprecated
                                BAR(""),
                        DAR
                      }
                      """, """
                      enum GroovyEnum {
                        FOO,
                        @Deprecated
                        BAR(""),
                        DAR
                      }
                      """);
  }

  public void testEnumAnnotationsSingleLine() {
    checkFormatting("""
                      enum GroovyEnum {
                        @Deprecated    BAR("")
                      }
                      """, """
                      enum GroovyEnum {
                        @Deprecated BAR("")
                      }
                      """);
  }

  public void testAlignFor() {
    getGroovySettings().ALIGN_MULTILINE_FOR = true;
    checkFormatting("""
                      for (int i = 3;
                      i<2;
                      i++) print 2
                      """, """
                      for (int i = 3;
                           i < 2;
                           i++) print 2
                      """);
  }

  public void testBinaryOperationSingOnNewLine() {
    getGroovySettings().BINARY_OPERATION_SIGN_ON_NEXT_LINE = true;

    checkFormatting("""
                      (1 +
                       2) + 3
                      """, """
                      (1
                          +
                          2)
                          + 3
                      """);
  }

  public void testParenthesized0() {
    getGroovySettings().PARENTHESES_EXPRESSION_LPAREN_WRAP = true;
    checkFormatting("""
                      (2+
                      3)
                      """, """
                      (
                          2 +
                              3)
                      """);
  }

  public void testParenthesized1() {
    getGroovySettings().PARENTHESES_EXPRESSION_RPAREN_WRAP = true;
    checkFormatting("""
                      (2+
                      3)
                      """, """
                      (2 +
                          3
                      )
                      """);
  }

  public void testParenthesized2() {
    getGroovySettings().PARENTHESES_EXPRESSION_LPAREN_WRAP = true;
    getGroovySettings().PARENTHESES_EXPRESSION_RPAREN_WRAP = true;
    checkFormatting("""
                      (2+
                      3)
                      """, """
                      (
                          2 +
                              3
                      )
                      """);
  }

  public void testParenthesized3() {
    getGroovySettings().PARENTHESES_EXPRESSION_LPAREN_WRAP = true;
    getGroovySettings().PARENTHESES_EXPRESSION_RPAREN_WRAP = true;
    getGroovySettings().ALIGN_MULTILINE_BINARY_OPERATION = true;
    checkFormatting("""
                      (2+
                      3)
                      """, """
                      (
                          2 +
                          3
                      )
                      """);
  }

  public void testAlignBinaryOperands() {
    getGroovySettings().ALIGN_MULTILINE_BINARY_OPERATION = true;
    checkFormatting("""
                      (2+
                      3 +
                      4)
                      """, """
                      (2 +
                       3 +
                       4)
                      """);
  }

  public void testConditional0() {
    getGroovySettings().ALIGN_MULTILINE_TERNARY_OPERATION = true;
    checkFormatting("""
                      print abc ?
                      cde:
                      xyz
                      """, """
                      print abc ?
                            cde :
                            xyz
                      """);
  }

  public void testConditional1() {
    getGroovySettings().ALIGN_MULTILINE_TERNARY_OPERATION = true;
    checkFormatting("""
                      print abc ?:
                      xyz
                      """, """
                      print abc ?:
                            xyz
                      """);
  }

  public void testConditional2() {
    getGroovySettings().ALIGN_MULTILINE_TERNARY_OPERATION = true;
    checkFormatting("""
                      print abc ? cde
                      :xyz""", """
                      print abc ? cde
                                : xyz""");
  }

  public void testGdocAsterisks() {
    checkFormatting("""
                      /*****
                      *
                      *****/
                      """, """
                      /*****
                       *
                       *****/
                      """);
  }

  public void testInKeyword() {
    checkFormatting("foo in  bar", "foo in bar");
  }

  public void testGDocAfterImports() { doTest(); }

  public void testGroovyDocAfterImports2() { doTest(); }

  public void testRegexExpressions() { doTest(); }

  public void testSpreadArg() { doTest(); }

  public void testExtraLines() { doTest(); }

  public void testNoLineFeedsInGString() { doTest(); }

  public void testNoLineFeedsInGString2() {
    getGroovySettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
    checkFormatting("""
                      println "--> ${value}"
                      """, """
                      println "--> ${value}"
                      """);
  }

  public void testNoLineFeedsInGString3() {
    getGroovySettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
    getGroovyCustomSettings().SPACE_WITHIN_GSTRING_INJECTION_BRACES = true;
    checkFormatting("""
                      println "--> ${value}"
                      """, """
                      println "--> ${ value }"
                      """);
  }

  public void testNoLineFeedsInGString4() {
    getGroovySettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
    checkFormatting("""
                      println "--> ${{value}}"
                      """, """
                      println "--> ${{ value }}"
                      """);
  }

  public void testNoLineFeedsInGString5() {
    getGroovySettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
    getGroovyCustomSettings().SPACE_WITHIN_GSTRING_INJECTION_BRACES = true;
    checkFormatting("""
                      println "--> ${{value}}"
                      """, """
                      println "--> ${ { value } }"
                      """);
  }

  public void testNoLineFeedsInGString6() {
    getGroovySettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
    checkFormatting("""
                      println "--> ${{it -> value}}"
                      """, """
                      println "--> ${{ it -> value }}"
                      """);
  }

  public void testNoLineFeedsInGStringMultiLine() {
    getGroovySettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
    checkFormatting("""
                      println ""\"--> ${value}""\"
                      """, """
                      println ""\"--> ${value}""\"
                      """);
  }

  public void testNoLineFeedsInGStringMultiLine2() {
    getGroovySettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
    checkFormatting("""
                      println ""\"--> ${{value}}""\"
                      """, """
                      println ""\"--> ${{ value }}""\"
                      """);
  }

  public void testNoLineFeedsInGStringUseFlyingGeeseBraces() {
    getGroovyCustomSettings().USE_FLYING_GEESE_BRACES = true;
    checkFormatting("""
                      println "--> ${{value}}"
                      """, """
                      println "--> ${{ value }}"
                      """);
  }

  public void testLongQualifiedName() {
    getGroovySettings().RIGHT_MARGIN = 26;
    getGroovySettings().WRAP_LONG_LINES = true;
    checkFormatting("""
                      def x = new aaaaaaaa.bbbbbbbbb.ccccccccc.foo()
                      """, """
                      def x = new aaaaaaaa.
                          bbbbbbbbb.
                          ccccccccc.foo()
                      """);
  }

  public void testSpacesAfterHardWrapMargin() {
    getGroovySettings().RIGHT_MARGIN = 10;
    getGroovySettings().WRAP_LONG_LINES = true;
    checkFormatting("""
                      aaaa.bb          \s
                      """, """
                      aaaa.bb          \s
                      """);
  }

  public void testGroovydoc() {
    checkFormatting("""
                      /** {@code
                       * {
                       *  foo
                       * }}
                       */
                      public static void main(String[] args) {
                      
                      }
                      """, """
                      /** {@code
                       * {
                       *  foo
                       * }}
                       */
                      public static void main(String[] args) {
                      
                      }
                      """);
  }

  public void testGroovydoc2() {
    checkFormatting("""
                      class Scratch {
                        /**
                         * {foo}
                         */
                        public static void main(String[] args) {
                      
                        }
                      }""", """
                      class Scratch {
                        /**
                         * {foo}
                         */
                        public static void main(String[] args) {
                      
                        }
                      }""");
  }

  public void testSafeIndex() {
    checkFormatting("[1]?[2]", "[1]?[2]");
  }

  private void doGeeseTest() {
    GroovyCodeStyleSettings customSettings = myTempSettings.getCustomSettings(GroovyCodeStyleSettings.class);
    boolean oldvalue = customSettings.USE_FLYING_GEESE_BRACES;
    try {
      customSettings.USE_FLYING_GEESE_BRACES = true;
      doTest();
    }
    finally {
      customSettings.USE_FLYING_GEESE_BRACES = oldvalue;
    }
  }
}
