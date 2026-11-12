// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.formatter;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterFixtureKt.groovyFormatterFixture;

@TestApplication
public class FormatterTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GroovyFormatterFixture> formatter =
    groovyFormatterFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "groovy/formatter/");

  @Test
  public void testAdding1() { doTest(); }

  @Test
  public void testArg1() { doTest(); }

  @Test
  public void testArg2() { doTest(); }

  @Test
  public void testBin1() { doTest(); }

  @Test
  public void testBin2() { doTest(); }

  @Test
  public void testBin3() { doTest(); }

  @Test
  public void testBlockExpr1() {
    //groovySettings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = false
    formatter.get().getGroovySettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
    doTest();
  }

  @Test
  public void testClass1() { doTest(); }

  @Test
  public void testClo1() { doTest(); }

  @Test
  public void testClo2() { doTest(); }

  @Test
  public void testClo3() { doTest(); }

  @Test
  public void testClo4() { doTest(); }

  @Test
  public void testColon1() { doTest(); }

  @Test
  public void testColon2() { doTest(); }

  @Test
  public void testCond1() { doTest(); }

  @Test
  public void testDoc1() { doTest(); }

  @Test
  public void testDoc2() { doTest(); }

  @Test
  public void testDoc3() { doTest(); }

  @Test
  public void testInlineTag() { doTest(); }

  @Test
  public void testDockter() { doTest(); }

  @Test
  public void testDot1() { doTest(); }

  @Test
  public void testDot2() { doTest(); }

  @Test
  public void testFor1() { doTest(); }

  @Test
  public void testFor2() { doTest(); }

  @Test
  public void testFor3() { doTest(); }

  @Test
  public void testFor4() { doTest(); }

  @Test
  public void testGbegin1() { doTest(); }

  @Test
  public void testGrvy1637() { doTest(); }

  @Test
  public void testGString1() { doTest(); }

  @Test
  public void testMap6() { doTest(); }

  @Test
  public void testMeth1() { doTest(); }

  @Test
  public void testMeth2() { doTest(); }

  @Test
  public void testMeth3() { doTest(); }

  @Test
  public void testMeth4() { doTest(); }

  @Test
  public void testMeth5() { doTest(); }

  @Test
  public void testMultistring1() { doTest(); }

  @Test
  public void testMultistring2() { doTest(); }

  @Test
  public void testNew1() { doTest(); }

  @Test
  public void testParam1() { doTest(); }

  @Test
  public void testParam2() { doTest(); }

  @Test
  public void testParen1() { doTest(); }

  @Test
  public void testPath1() { doTest(); }

  @Test
  public void testPointer1() { doTest(); }

  @Test
  public void testRange1() { doTest(); }

  @Test
  public void testRegex1() { doTest(); }

  @Test
  public void testRegex2() { doTest(); }

  @Test
  public void testSh1() { doTest(); }

  @Test
  public void testSh2() { doTest(); }

  @Test
  public void testSqr1() { doTest(); }

  @Test
  public void testSqr2() { doTest(); }

  @Test
  public void testSqr3() { doTest(); }

  @Test
  public void testString1() { doTest(); }

  @Test
  public void testSuper1() { doTest(); }

  @Test
  public void testSwitch1() { doTest(); }

  @Test
  public void testSwitch2() { doTest(); }

  @Test
  public void testSwitch3() { doTest(); }

  @Test
  public void testSwitch4() { doTest(); }

  @Test
  public void testSwitch5() { doTest(); }

  @Test
  public void testSwitch6() { doTest(); }

  @Test
  public void testSwitch7() { doTest(); }

  @Test
  public void testSwitch8() { doTest(); }

  @Test
  public void testSwitchexpr1() { doTest(); }

  @Test
  public void testSwitchexpr2() { doTest(); }

  @Test
  public void testSwitchexpr3() { doTest(); }

  @Test
  public void testSwitchexpr4() { doTest(); }

  @Test
  public void testSwitchexpr5() { doTest(); }

  @Test
  public void testSwitchexpr6() { doTest(); }

  @Test
  public void testSwitchexpr7() { doTest(); }

  @Test
  public void testType1() { doTest(); }

  @Test
  public void testTypeparam1() { doTest(); }

  @Test
  public void testUn1() { doTest(); }

  @Test
  public void testUn2() { doTest(); }

  @Test
  public void testUn3() { doTest(); }

  @Test
  public void testWhile1() { doTest(); }

  @Test
  public void testWhile2() { doTest(); }

  @Test
  public void testWhileCStyle() { doTest(); }

  @Test
  public void testFields() { doTest(); }

  @Test
  public void testClosureAfterLineComment() {
    formatter.get().getGroovySettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
    doTest();
  }

  @Test
  public void testAnnotationOnSeparateLine() { doTest(); }

  @Test
  public void testAlignMultipleVariables() { doTest(); }

  @Test
  public void testAlignMultipleVariablesLabeled() { doTest(); }

  @Test
  public void testElseIfs() {
    formatter.get().getGroovySettings().SPECIAL_ELSE_IF_TREATMENT = false;
    doTest();
  }

  @Test
  public void testElseIfsSpecial() { doTest(); }

  @Test
  public void testVarargDeclaration() { doTest(); }

  @Test
  public void testPreserveSpaceBeforeClosureParameters() { doTest(); }

  @Test
  public void testPreserveGroovydoc() { doTest(); }

  @Test
  public void testGroovydocNoExtraSpace() { doTest(); }

  @Test
  public void testCaseInSwitch() {
    formatter.get().getGroovySettings().INDENT_CASE_FROM_SWITCH = false;
    doTest();
  }

  @Test
  public void testCaseInSwitchIndented() { doTest(); }

  @Test
  public void testStuffAfterLineComments() { doTest(); }

  @Test
  public void testAnonymousInCall() {
    formatter.get().getGroovySettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest();
  }

  public void _testLabelIndent() {
    formatter.get().getGroovySettings().getIndentOptions().LABEL_INDENT_SIZE = -2;
    doTest();
  }

  @Test
  public void testClosureParametersAligned() {
    formatter.get().getGroovySettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    doTest();
  }

  @Test
  public void testAlignClosureBraceWithCall() { doTest(); }

  @Test
  public void testFlyingGeese() {
    formatter.get().getTempSettings().getCustomSettings(GroovyCodeStyleSettings.class).USE_FLYING_GEESE_BRACES = true;
    doTest();
  }

  @Test
  public void testSpaceAfterTypeCast() {
    formatter.get().getGroovySettings().SPACE_AFTER_TYPE_CAST = false;
    formatter.get().getGroovySettings().SPACE_WITHIN_CAST_PARENTHESES = true;
    doTest();
  }

  @Test
  public void testChainCallWithClosures() { doTest(); }

  @Test
  public void testFormatDiamonds() { doTest(); }

  @Test
  public void testFormatAnonymousDiamonds() { doTest(); }

  @Test
  public void testPreserveChainingLineBreaks() { doTest(); }

  @Test
  public void testMultilineEolComment() { doTest(); }

  @Test
  public void testRedundantClosureSpace() { doTest(); }

  @Test
  public void testIndentNamedArguments() { doTest(); }

  @Test
  public void testIndentAssigned() { doTest(); }

  @Test
  public void testCommentBeforeMultilineString() { doTest(); }

  @Test
  public void testMethodSemicolons() { doTest(); }

  @Test
  public void testNoFlyingGeese() {
    formatter.get().getTempSettings().getCustomSettings(GroovyCodeStyleSettings.class).SPACE_IN_NAMED_ARGUMENT = false;
    formatter.get().getTempSettings().getCustomSettings(GroovyCodeStyleSettings.class).USE_FLYING_GEESE_BRACES = false;
    doTest();
  }

  @Test
  public void testAlignChainedCalls() {
    formatter.get().getGroovySettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    doTest();
  }

  @Test
  public void testAlignBinaries() {
    formatter.get().getGroovySettings().ALIGN_MULTILINE_BINARY_OPERATION = true;
    doTest();
  }

  @Test
  public void testAlignTernaries() {
    formatter.get().getGroovySettings().ALIGN_MULTILINE_TERNARY_OPERATION = true;
    doTest();
  }

  @Test
  public void testAlignAssignments() {
    formatter.get().getGroovySettings().ALIGN_MULTILINE_ASSIGNMENT = true;
    doTest();
  }

  public void doTest() {
    LightJavaCodeInsightTestFixture myFixture = formatter.get().getFixture();
    final Iterator<String> iterator = TestUtils.readInput(myFixture.getTestDataPath() + myFixture.getTestName(true) + ".test").iterator();
    String before = iterator.hasNext() ? iterator.next() : null;
    String after = iterator.hasNext() ? iterator.next() : null;

    formatter.get().checkFormatting(before, StringUtil.trimEnd(after, "\n"));
  }

  @Test
  public void testJavadocLink() {
    // Check that no unnecessary white spaces are introduced for the javadoc link element.
    // Check IDEA-57573 for more details.
    doTest();
  }

  @Test
  public void testFieldInColumnsAlignment() {
    formatter.get().getGroovySettings().ALIGN_GROUP_FIELD_DECLARATIONS = true;
    formatter.get().getGroovySettings().FIELD_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;
    formatter.get().getGroovySettings().VARIABLE_ANNOTATION_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP;

    doTest();
  }

  @Test
  public void testGeese1() { doGeeseTest(); }

  @Test
  public void testGeese2() { doGeeseTest(); }

  @Test
  public void testGeese3() { doGeeseTest(); }

  @Test
  public void testGeese4() { doGeeseTest(); }

  @Test
  public void testGeese5() { doGeeseTest(); }

  @Test
  public void testGeese6() { doGeeseTest(); }

  @Test
  public void testGeese7() { doGeeseTest(); }

  @Test
  public void testGeese8() { doGeeseTest(); }

  @Test
  public void testMapInArgumentList() { doTest(); }

  @Test
  public void testMapInArgList2() {
    formatter.get().getTempSettings().getCustomSettings(GroovyCodeStyleSettings.class).ALIGN_NAMED_ARGS_IN_MAP = true;
    doTest();
  }

  @Test
  public void testForceBraces() {
    formatter.get().getGroovySettings().IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;
    formatter.get().getGroovySettings().FOR_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;
    formatter.get().getGroovySettings().WHILE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_IF_MULTILINE;
    doTest();
  }

  @Test
  public void testIndentAfterClosureQualifier() { doTest(); }

  @Test
  public void testIndentAfterClosureQualifier2() { doTest(); }

  @Test
  public void testIndentAfterClosureQualifier3() { doTest(); }

  @Test
  public void testChainCallFieldIndent() {
    formatter.get().getGroovySettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    doTest();
  }

  @Test
  public void testChainCallFieldIndent2() {
    formatter.get().getGroovySettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    doTest();
  }

  @Test
  public void testChainCallFieldIndent3() {
    formatter.get().getGroovySettings().ALIGN_MULTILINE_CHAINED_METHODS = true;
    doTest();
  }

  @Test
  public void testAssertDescriptionIndent() { doTest(); }

  @Test
  public void testPackageDef1() { doTest(); }

  @Test
  public void testPackageDef2() { doTest(); }

  @Test
  public void testAnnotationArgs1() { doTest(); }

  @Test
  public void testAnnotationArgs2() { doTest(); }

  @Test
  public void testImplementsList() { doTest(); }

  @Test
  public void testSimpleClassInOneLine() {
    formatter.get().getGroovySettings().KEEP_SIMPLE_CLASSES_IN_ONE_LINE = false;
    formatter.get().checkFormatting("""
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

  @Test
  public void testSimpleMethodInOneLine() {
    formatter.get().getGroovySettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = false;
    formatter.get().checkFormatting("""
                      def foo() {2}
                      """, """
                      def foo() {
                        2
                      }
                      """);

    formatter.get().getGroovySettings().KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
    formatter.get().checkFormatting("""
                      def foo() {2}
                      """, """
                      def foo() { 2 }
                      """);
  }

  @Test
  public void testSimpleBlocksInOneLine() {
    formatter.get().getGroovySettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    formatter.get().checkFormatting("""
                      if (abc) {return 2}
                      """, """
                      if (abc) { return 2 }
                      """);

    formatter.get().getGroovySettings().KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = false;
    formatter.get().checkFormatting("""
                      if (abc) {return 2}
                      """, """
                      if (abc) {
                        return 2
                      }
                      """);
  }

  @Test
  public void testControlStatementsInOneLine() {
    formatter.get().getGroovySettings().KEEP_CONTROL_STATEMENT_IN_ONE_LINE = true;
    formatter.get().checkFormatting("""
                      if (abc) return 2
                      """, """
                      if (abc) return 2
                      """);
    formatter.get().checkFormatting("""
                      while (abc) return 2
                      """, """
                      while (abc) return 2
                      """);
    formatter.get().checkFormatting("""
                      for (abc in abc) return 2
                      """, """
                      for (abc in abc) return 2
                      """);

    formatter.get().getGroovySettings().KEEP_CONTROL_STATEMENT_IN_ONE_LINE = false;
    formatter.get().checkFormatting("""
                      if (abc) return 2
                      """, """
                      if (abc)
                        return 2
                      """);
    formatter.get().checkFormatting("""
                      while (abc) return 2
                      """, """
                      while (abc)
                        return 2
                      """);

    formatter.get().checkFormatting("""
                      for (;abc;) return 2
                      """, """
                      for (; abc;)
                        return 2
                      """);
  }

  @Test
  public void testWrapThrows() {
    formatter.get().getGroovySettings().THROWS_KEYWORD_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

    formatter.get().checkFormatting("""
                      def foo() throws X {}
                      """, """
                      def foo()
                          throws X {}
                      """);
  }

  @Test
  public void testSpacesWithinClosure0() {
    formatter.get().getGroovySettings().SPACE_WITHIN_BRACES = true;
    formatter.get().checkFormatting("""
                      def cl = {print 2}""", """
                      def cl = { print 2 }""");
  }

  @Test
  public void testSpacesWithinClosure1() {
    formatter.get().getGroovySettings().SPACE_WITHIN_BRACES = true;
    formatter.get().checkFormatting("""
                      def cl = {
                      print 2}
                      """, """
                      def cl = {
                        print 2
                      }
                      """);
  }

  @Test
  public void testSpacesWithinClosure2() {
    formatter.get().getGroovySettings().SPACE_WITHIN_BRACES = true;
    formatter.get().checkFormatting("""
                      def cl = {->
                      print 2}
                      """, """
                      def cl = { ->
                        print 2
                      }
                      """);
  }

  @Test
  public void testSpacesWithinClosure3() {
    formatter.get().getGroovySettings().SPACE_WITHIN_BRACES = true;
    formatter.get().checkFormatting("""
                      def cl = {def a->
                      print 2}
                      """, """
                      def cl = { def a ->
                        print 2
                      }
                      """);
  }

  @Test
  public void testSpacesWithinClosure4() {
    formatter.get().getGroovySettings().SPACE_WITHIN_BRACES = true;
    formatter.get().checkFormatting("""
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

  @Test
  public void testSpacesWithinClosure5() {
    formatter.get().getGroovySettings().SPACE_WITHIN_BRACES = false;
    formatter.get().checkFormatting("""
                      def cl = { print 2 }""", """
                      def cl = {print 2}""");
  }

  @Test
  public void testSpacesWithinClosure6() {
    formatter.get().getGroovySettings().SPACE_WITHIN_BRACES = false;
    formatter.get().checkFormatting("""
                      def cl = {
                      print 2}
                      """, """
                      def cl = {
                        print 2
                      }
                      """);
  }

  @Test
  public void testSpacesWithinClosure7() {
    formatter.get().getGroovySettings().SPACE_WITHIN_BRACES = false;
    formatter.get().checkFormatting("""
                      def cl = { ->
                      print 2}
                      """, """
                      def cl = {->
                        print 2
                      }
                      """);
  }

  @Test
  public void testSpacesWithinClosure8() {
    formatter.get().getGroovySettings().SPACE_WITHIN_BRACES = false;
    formatter.get().checkFormatting("""
                      def cl = { def a->
                      print 2}
                      """, """
                      def cl = {def a ->
                        print 2
                      }
                      """);
  }

  @Test
  public void testSpacesWithinClosure9() {
    formatter.get().getGroovySettings().SPACE_WITHIN_BRACES = false;
    formatter.get().checkFormatting("""
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

  @Test
  public void testLineFeedsInMethodParams0() {
    formatter.get().getGroovySettings().METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    formatter.get().checkFormatting("""
                      def foo(String s,
                      int x) {}
                      """, """
                      def foo(
                          String s,
                          int x) {}
                      """);
  }

  @Test
  public void testLineFeedsInMethodParams1() {
    formatter.get().getGroovySettings().METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    formatter.get().checkFormatting("""
                      def foo(String s,
                      int x) {}
                      """, """
                      def foo(String s,
                              int x
                      ) {}
                      """);
  }

  @Test
  public void testLineFeedsInMethodParams2() {
    formatter.get().getGroovySettings().METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    formatter.get().getGroovySettings().METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    formatter.get().checkFormatting("""
                      def foo(String s,
                      int x) {}
                      """, """
                      def foo(
                          String s,
                          int x
                      ) {}
                      """);
  }

  @Test
  public void testLineFeedsInMethodParams3() {
    formatter.get().getGroovySettings().METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    formatter.get().getGroovySettings().METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    formatter.get().getGroovySettings().SPACE_WITHIN_METHOD_PARENTHESES = true;
    formatter.get().checkFormatting("""
                      def foo(String s, int x) {}
                      """, """
                      def foo( String s, int x ) {}
                      """);
  }

  @Test
  public void testLineFeedsInMethodCall0() {
    formatter.get().getGroovySettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    formatter.get().checkFormatting("""
                      foo(s,
                          x)
                      """, """
                      foo(
                          s,
                          x)
                      """);
  }

  @Test
  public void testLineFeedsInMethodCall1() {
    formatter.get().getGroovySettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    formatter.get().checkFormatting("""
                      foo(s,
                      x)
                      """, """
                      foo(s,
                          x
                      )
                      """);
  }

  @Test
  public void testLineFeedsInMethodCall2() {
    formatter.get().getGroovySettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    formatter.get().getGroovySettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    formatter.get().checkFormatting("""
                      foo(s,
                       x)
                      """, """
                      foo(
                          s,
                          x
                      )
                      """);
  }

  @Test
  public void testLineFeedsInMethodCall3() {
    formatter.get().getGroovySettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    formatter.get().getGroovySettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    formatter.get().getGroovySettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    formatter.get().checkFormatting("""
                      foo(s, x)
                      """, """
                      foo( s, x )
                      """);
  }

  @Test
  public void testLineFeedsInMethodCall4() {
    formatter.get().getGroovySettings().CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    formatter.get().getGroovySettings().CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
    formatter.get().getGroovySettings().SPACE_WITHIN_EMPTY_METHOD_CALL_PARENTHESES = true;
    formatter.get().checkFormatting("""
                      foo()
                      """, """
                      foo( )
                      """);
  }

  @Test
  public void testAlignMethodParentheses() {
    formatter.get().getGroovySettings().ALIGN_MULTILINE_METHOD_BRACKETS = true;
    formatter.get().checkFormatting("""
                      def foooo(
                      String s
                      ) {}
                      """, """
                      def foooo(
                          String s
                               ) {}
                      """);
  }

  @Test
  public void testEnumAnnotations() {
    formatter.get().checkFormatting("""
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

  @Test
  public void testEnumAnnotationsSingleLine() {
    formatter.get().checkFormatting("""
                      enum GroovyEnum {
                        @Deprecated    BAR("")
                      }
                      """, """
                      enum GroovyEnum {
                        @Deprecated BAR("")
                      }
                      """);
  }

  @Test
  public void testAlignFor() {
    formatter.get().getGroovySettings().ALIGN_MULTILINE_FOR = true;
    formatter.get().checkFormatting("""
                      for (int i = 3;
                      i<2;
                      i++) print 2
                      """, """
                      for (int i = 3;
                           i < 2;
                           i++) print 2
                      """);
  }

  @Test
  public void testBinaryOperationSingOnNewLine() {
    formatter.get().getGroovySettings().BINARY_OPERATION_SIGN_ON_NEXT_LINE = true;

    formatter.get().checkFormatting("""
                      (1 +
                       2) + 3
                      """, """
                      (1
                          +
                          2)
                          + 3
                      """);
  }

  @Test
  public void testParenthesized0() {
    formatter.get().getGroovySettings().PARENTHESES_EXPRESSION_LPAREN_WRAP = true;
    formatter.get().checkFormatting("""
                      (2+
                      3)
                      """, """
                      (
                          2 +
                              3)
                      """);
  }

  @Test
  public void testParenthesized1() {
    formatter.get().getGroovySettings().PARENTHESES_EXPRESSION_RPAREN_WRAP = true;
    formatter.get().checkFormatting("""
                      (2+
                      3)
                      """, """
                      (2 +
                          3
                      )
                      """);
  }

  @Test
  public void testParenthesized2() {
    formatter.get().getGroovySettings().PARENTHESES_EXPRESSION_LPAREN_WRAP = true;
    formatter.get().getGroovySettings().PARENTHESES_EXPRESSION_RPAREN_WRAP = true;
    formatter.get().checkFormatting("""
                      (2+
                      3)
                      """, """
                      (
                          2 +
                              3
                      )
                      """);
  }

  @Test
  public void testParenthesized3() {
    formatter.get().getGroovySettings().PARENTHESES_EXPRESSION_LPAREN_WRAP = true;
    formatter.get().getGroovySettings().PARENTHESES_EXPRESSION_RPAREN_WRAP = true;
    formatter.get().getGroovySettings().ALIGN_MULTILINE_BINARY_OPERATION = true;
    formatter.get().checkFormatting("""
                      (2+
                      3)
                      """, """
                      (
                          2 +
                          3
                      )
                      """);
  }

  @Test
  public void testAlignBinaryOperands() {
    formatter.get().getGroovySettings().ALIGN_MULTILINE_BINARY_OPERATION = true;
    formatter.get().checkFormatting("""
                      (2+
                      3 +
                      4)
                      """, """
                      (2 +
                       3 +
                       4)
                      """);
  }

  @Test
  public void testConditional0() {
    formatter.get().getGroovySettings().ALIGN_MULTILINE_TERNARY_OPERATION = true;
    formatter.get().checkFormatting("""
                      print abc ?
                      cde:
                      xyz
                      """, """
                      print abc ?
                            cde :
                            xyz
                      """);
  }

  @Test
  public void testConditional1() {
    formatter.get().getGroovySettings().ALIGN_MULTILINE_TERNARY_OPERATION = true;
    formatter.get().checkFormatting("""
                      print abc ?:
                      xyz
                      """, """
                      print abc ?:
                            xyz
                      """);
  }

  @Test
  public void testConditional2() {
    formatter.get().getGroovySettings().ALIGN_MULTILINE_TERNARY_OPERATION = true;
    formatter.get().checkFormatting("""
                      print abc ? cde
                      :xyz""", """
                      print abc ? cde
                                : xyz""");
  }

  @Test
  public void testGdocAsterisks() {
    formatter.get().checkFormatting("""
                      /*****
                      *
                      *****/
                      """, """
                      /*****
                       *
                       *****/
                      """);
  }

  @Test
  public void testInKeyword() {
    formatter.get().checkFormatting("foo in  bar", "foo in bar");
  }

  @Test
  public void testGDocAfterImports() { doTest(); }

  @Test
  public void testGroovyDocAfterImports2() { doTest(); }

  @Test
  public void testRegexExpressions() { doTest(); }

  @Test
  public void testSpreadArg() { doTest(); }

  @Test
  public void testExtraLines() { doTest(); }

  @Test
  public void testNoLineFeedsInGString() { doTest(); }

  @Test
  public void testNoLineFeedsInGString2() {
    formatter.get().getGroovySettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
    formatter.get().checkFormatting("""
                      println "--> ${value}"
                      """, """
                      println "--> ${value}"
                      """);
  }

  @Test
  public void testNoLineFeedsInGString3() {
    formatter.get().getGroovySettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
    formatter.get().getGroovyCustomSettings().SPACE_WITHIN_GSTRING_INJECTION_BRACES = true;
    formatter.get().checkFormatting("""
                      println "--> ${value}"
                      """, """
                      println "--> ${ value }"
                      """);
  }

  @Test
  public void testNoLineFeedsInGString4() {
    formatter.get().getGroovySettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
    formatter.get().checkFormatting("""
                      println "--> ${{value}}"
                      """, """
                      println "--> ${{ value }}"
                      """);
  }

  @Test
  public void testNoLineFeedsInGString5() {
    formatter.get().getGroovySettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
    formatter.get().getGroovyCustomSettings().SPACE_WITHIN_GSTRING_INJECTION_BRACES = true;
    formatter.get().checkFormatting("""
                      println "--> ${{value}}"
                      """, """
                      println "--> ${ { value } }"
                      """);
  }

  @Test
  public void testNoLineFeedsInGString6() {
    formatter.get().getGroovySettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
    formatter.get().checkFormatting("""
                      println "--> ${{it -> value}}"
                      """, """
                      println "--> ${{ it -> value }}"
                      """);
  }

  @Test
  public void testNoLineFeedsInGStringMultiLine() {
    formatter.get().getGroovySettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
    formatter.get().checkFormatting("""
                      println ""\"--> ${value}""\"
                      """, """
                      println ""\"--> ${value}""\"
                      """);
  }

  @Test
  public void testNoLineFeedsInGStringMultiLine2() {
    formatter.get().getGroovySettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
    formatter.get().checkFormatting("""
                      println ""\"--> ${{value}}""\"
                      """, """
                      println ""\"--> ${{ value }}""\"
                      """);
  }

  @Test
  public void testNoLineFeedsInGStringUseFlyingGeeseBraces() {
    formatter.get().getGroovyCustomSettings().USE_FLYING_GEESE_BRACES = true;
    formatter.get().checkFormatting("""
                      println "--> ${{value}}"
                      """, """
                      println "--> ${{ value }}"
                      """);
  }

  @Test
  public void testLongQualifiedName() {
    formatter.get().getGroovySettings().RIGHT_MARGIN = 26;
    formatter.get().getGroovySettings().WRAP_LONG_LINES = true;
    formatter.get().checkFormatting("""
                      def x = new aaaaaaaa.bbbbbbbbb.ccccccccc.foo()
                      """, """
                      def x = new aaaaaaaa.
                          bbbbbbbbb.
                          ccccccccc.foo()
                      """);
  }

  @Test
  public void testSpacesAfterHardWrapMargin() {
    formatter.get().getGroovySettings().RIGHT_MARGIN = 10;
    formatter.get().getGroovySettings().WRAP_LONG_LINES = true;
    formatter.get().checkFormatting("""
                      aaaa.bb          \s
                      """, """
                      aaaa.bb          \s
                      """);
  }

  @Test
  public void testGroovydoc() {
    formatter.get().checkFormatting("""
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

  @Test
  public void testGroovydoc2() {
    formatter.get().checkFormatting("""
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

  @Test
  public void testSafeIndex() {
    formatter.get().checkFormatting("[1]?[2]", "[1]?[2]");
  }

  private void doGeeseTest() {
    GroovyCodeStyleSettings customSettings = formatter.get().getTempSettings().getCustomSettings(GroovyCodeStyleSettings.class);
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
