// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.formatter;

import org.jetbrains.plugins.groovy.util.TestUtils;

public class GroovyCodeStyleFormatterTest extends GroovyFormatterTestCase {
  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/codeStyle/";
  }

  public void testClass_decl1() throws Throwable { doTest(); }

  public void testClass_decl2() throws Throwable { doTest(); }

  public void testClass_decl3() throws Throwable { doTest(); }

  public void testClass_decl4() throws Throwable { doTest(); }

  public void testComm_at_first_column1() throws Throwable { doTest(); }

  public void testComm_at_first_column2() throws Throwable { doTest(); }

  public void testFor1() throws Throwable { doTest(); }

  public void testFor2() throws Throwable { doTest(); }

  public void testGRVY_1134() throws Throwable { doTest(); }

  public void testIf1() throws Throwable { doTest(); }

  public void testMethod_call_par1() throws Throwable { doTest(); }

  public void testMethod_call_par2() throws Throwable { doTest(); }

  public void testArgumentsWrapIfLong() throws Throwable { doTest(); }

  public void testArgumentsDontWrapAlign() throws Throwable { doTest(); }

  public void testArgumentsWrapAlways() throws Throwable { doTest(); }

  public void testArgumentsWrapAlwaysNl() throws Throwable { doTest(); }

  public void testArgumentsWrapAlwaysAlign() throws Throwable { doTest(); }

  public void testParametersDontWrapAlign() throws Throwable { doTest(); }

  public void testParametersWrapAlways() throws Throwable { doTest(); }

  public void testParametersWrapAlwaysDontAlign() throws Throwable { doTest(); }

  public void testParametersWrapAlwaysNl() throws Throwable { doTest(); }

  public void testParametersComments() throws Throwable { doTest(); }

  public void testMethod_decl1() throws Throwable { doTest(); }

  public void testMethod_decl2() throws Throwable { doTest(); }

  public void testMethod_decl_par1() throws Throwable { doTest(); }

  public void testSwitch1() throws Throwable { doTest(); }

  public void testSwitchexpr1() throws Throwable { doTest(); }

  public void testSynch1() throws Throwable { doTest(); }

  public void testTry1() throws Throwable { doTest(); }

  public void testTry2() throws Throwable { doTest(); }

  public void testTryResourcesSpaces() throws Throwable { doTest(); }

  public void testTryResourcesDontWrap() throws Throwable { doTest(); }

  public void testTryResourcesWrapAlways() throws Throwable { doTest(); }

  public void testTryResourcesWrapAlwaysDontAlign() throws Throwable { doTest(); }

  public void testTryResourcesWrapAlwaysNl() throws Throwable { doTest(); }

  public void testWhile1() throws Throwable { doTest(); }

  public void testWhile2() throws Throwable { doTest(); }

  public void testDoWhileSpaces() throws Throwable { doTest(); }

  public void testDoWhileWrapping() throws Throwable { doTest(); }

  public void testDoWhileForceBraces() throws Throwable { doTest(); }

  public void testDoWhileForceBracesMultiline() throws Throwable { doTest(); }

  public void testWithin_brackets1() throws Throwable { doTest(); }

  public void testSpace_in_named_arg_true() throws Throwable { doTest(); }

  public void testSpace_in_named_arg_false() throws Throwable { doTest(); }

  public void testAssertSeparatorSpace() throws Throwable { doTest(); }

  public void testAssertSeparatorNoSpace() throws Throwable { doTest(); }

  public void testSpaceInNamedArgBeforeColon() throws Throwable { doTest(); }

  public void testAnonymousVsLBraceOnNewLine() throws Throwable { doTest(); }

  public void testBracesNextLine() throws Throwable { doTest(); }

  public void testBracesNextLineShifted() throws Throwable { doTest(); }

  public void testBracesNextLineShifted2() throws Throwable { doTest(); }

  public void testBracesEndLine() throws Throwable { doTest(); }

  public void testArrayInitializerSpaces() throws Throwable { doTest(); }

  public void testArrayInitializerDontWrap() throws Throwable { doTest(); }

  public void testArrayInitializerWrapAlways() throws Throwable { doTest(); }

  public void testArrayInitializerWrapAlwaysAlign() throws Throwable { doTest(); }

  public void testArrayInitializerWrapAlwaysNl() throws Throwable { doTest(); }

  public void testLabelIndentAbsolute() throws Throwable { doTest(); }

  public void testLabelIndentRelative() throws Throwable { doTest(); }

  public void testLabelIndentRelativeReverse() throws Throwable { doTest(); }

  public void testBlankLinesInCode() throws Throwable { doTest(); }

  public void testAlignFields0BlankLines() throws Throwable { doTest(); }

  public void testAlignFields1BlankLine() throws Throwable { doTest(); }

  public void testAlignFields2BlankLines() throws Throwable { doTest(); }

  public void testBooleanOperators() throws Throwable { doTest(); }

  private void doTest() throws Throwable {
    doTest(getTestName(true) + ".test");
  }
}
