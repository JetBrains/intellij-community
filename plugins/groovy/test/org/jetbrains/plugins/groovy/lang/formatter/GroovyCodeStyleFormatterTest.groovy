// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.formatter


import org.jetbrains.plugins.groovy.util.TestUtils

class GroovyCodeStyleFormatterTest extends GroovyFormatterTestCase {

  final String basePath = TestUtils.testDataPath + "groovy/codeStyle/"

  void testClass_decl1() throws Throwable { doTest() }

  void testClass_decl2() throws Throwable { doTest() }

  void testClass_decl3() throws Throwable { doTest() }

  void testClass_decl4() throws Throwable { doTest() }

  void testComm_at_first_column1() throws Throwable { doTest() }

  void testComm_at_first_column2() throws Throwable { doTest() }

  void testFor1() throws Throwable { doTest() }

  void testFor2() throws Throwable { doTest() }

  void testGRVY_1134() throws Throwable { doTest() }

  void testIf1() throws Throwable { doTest() }

  void testMethod_call_par1() throws Throwable { doTest() }

  void testMethod_call_par2() throws Throwable { doTest() }

  void testArgumentsWrapIfLong() { doTest() }

  void testArgumentsDontWrapAlign() { doTest() }

  void testArgumentsWrapAlways() { doTest() }

  void testArgumentsWrapAlwaysNl() { doTest() }

  void testArgumentsWrapAlwaysAlign() { doTest() }

  void testParametersDontWrapAlign() { doTest() }

  void testParametersWrapAlways() { doTest() }

  void testParametersWrapAlwaysDontAlign() { doTest() }

  void testParametersWrapAlwaysNl() { doTest() }

  void testParametersComments() { doTest() }

  void testMethod_decl1() throws Throwable { doTest() }

  void testMethod_decl2() throws Throwable { doTest() }

  void testMethod_decl_par1() throws Throwable { doTest() }

  void testSwitch1() throws Throwable { doTest() }

  void testSwitchexpr1() throws Throwable { doTest() }

  void testSynch1() throws Throwable { doTest() }

  void testTry1() throws Throwable { doTest() }

  void testTry2() throws Throwable { doTest() }

  void testTryResourcesSpaces() { doTest() }

  void testTryResourcesDontWrap() { doTest() }

  void testTryResourcesWrapAlways() { doTest() }

  void testTryResourcesWrapAlwaysDontAlign() { doTest() }

  void testTryResourcesWrapAlwaysNl() { doTest() }

  void testWhile1() throws Throwable { doTest() }

  void testWhile2() throws Throwable { doTest() }

  void testDoWhileSpaces() { doTest() }

  void testDoWhileWrapping() { doTest() }

  void testDoWhileForceBraces() { doTest() }

  void testDoWhileForceBracesMultiline() { doTest() }

  void testWithin_brackets1() throws Throwable { doTest() }

  void testSpace_in_named_arg_true() throws Throwable { doTest() }

  void testSpace_in_named_arg_false() throws Throwable { doTest() }

  void testAssertSeparatorSpace() { doTest() }

  void testAssertSeparatorNoSpace() { doTest() }

  void testSpaceInNamedArgBeforeColon() { doTest() }

  void testAnonymousVsLBraceOnNewLine() { doTest() }

  void testBracesNextLine() { doTest() }

  void testBracesNextLineShifted() { doTest() }

  void testBracesNextLineShifted2() { doTest() }

  void testBracesEndLine() { doTest() }

  void testArrayInitializerSpaces() { doTest() }

  void testArrayInitializerDontWrap() { doTest() }

  void testArrayInitializerWrapAlways() { doTest() }

  void testArrayInitializerWrapAlwaysAlign() { doTest() }

  void testArrayInitializerWrapAlwaysNl() { doTest() }

  void testLabelIndentAbsolute() { doTest() }

  void testLabelIndentRelative() { doTest() }

  void testLabelIndentRelativeReverse() { doTest() }

  void testBlankLinesInCode() { doTest() }

  void testAlignFields0BlankLines() { doTest() }

  void testAlignFields1BlankLine() { doTest() }

  void testAlignFields2BlankLines() { doTest() }

  private doTest() {
    doTest(getTestName(true) + ".test")
  }
}
