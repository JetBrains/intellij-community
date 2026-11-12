// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.formatter;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterFixtureKt.groovyFormatterFixture;

@TestApplication
public class GroovyCodeStyleFormatterTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GroovyFormatterFixture> formatter =
    groovyFormatterFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "groovy/codeStyle/");

  @Test
  public void testClass_decl1() { doTest(); }

  @Test
  public void testClass_decl2() { doTest(); }

  @Test
  public void testClass_decl3() { doTest(); }

  @Test
  public void testClass_decl4() { doTest(); }

  @Test
  public void testComm_at_first_column1() { doTest(); }

  @Test
  public void testComm_at_first_column2() { doTest(); }

  @Test
  public void testFor1() { doTest(); }

  @Test
  public void testFor2() { doTest(); }

  @Test
  public void testGRVY_1134() { doTest(); }

  @Test
  public void testIf1() { doTest(); }

  @Test
  public void testMethod_call_par1() { doTest(); }

  @Test
  public void testMethod_call_par2() { doTest(); }

  @Test
  public void testArgumentsWrapIfLong() { doTest(); }

  @Test
  public void testArgumentsDontWrapAlign() { doTest(); }

  @Test
  public void testArgumentsWrapAlways() { doTest(); }

  @Test
  public void testArgumentsWrapAlwaysNl() { doTest(); }

  @Test
  public void testArgumentsWrapAlwaysAlign() { doTest(); }

  @Test
  public void testParametersDontWrapAlign() { doTest(); }

  @Test
  public void testParametersWrapAlways() { doTest(); }

  @Test
  public void testParametersWrapAlwaysDontAlign() { doTest(); }

  @Test
  public void testParametersWrapAlwaysNl() { doTest(); }

  @Test
  public void testParametersComments() { doTest(); }

  @Test
  public void testMethod_decl1() { doTest(); }

  @Test
  public void testMethod_decl2() { doTest(); }

  @Test
  public void testMethod_decl_par1() { doTest(); }

  @Test
  public void testSwitch1() { doTest(); }

  @Test
  public void testSwitchexpr1() { doTest(); }

  @Test
  public void testSynch1() { doTest(); }

  @Test
  public void testTry1() { doTest(); }

  @Test
  public void testTry2() { doTest(); }

  @Test
  public void testTryResourcesSpaces() { doTest(); }

  @Test
  public void testTryResourcesDontWrap() { doTest(); }

  @Test
  public void testTryResourcesWrapAlways() { doTest(); }

  @Test
  public void testTryResourcesWrapAlwaysDontAlign() { doTest(); }

  @Test
  public void testTryResourcesWrapAlwaysNl() { doTest(); }

  @Test
  public void testWhile1() { doTest(); }

  @Test
  public void testWhile2() { doTest(); }

  @Test
  public void testDoWhileSpaces() { doTest(); }

  @Test
  public void testDoWhileWrapping() { doTest(); }

  @Test
  public void testDoWhileForceBraces() { doTest(); }

  @Test
  public void testDoWhileForceBracesMultiline() { doTest(); }

  @Test
  public void testWithin_brackets1() { doTest(); }

  @Test
  public void testSpace_in_named_arg_true() { doTest(); }

  @Test
  public void testSpace_in_named_arg_false() { doTest(); }

  @Test
  public void testAssertSeparatorSpace() { doTest(); }

  @Test
  public void testAssertSeparatorNoSpace() { doTest(); }

  @Test
  public void testSpaceInNamedArgBeforeColon() { doTest(); }

  @Test
  public void testAnonymousVsLBraceOnNewLine() { doTest(); }

  @Test
  public void testBracesNextLine() { doTest(); }

  @Test
  public void testBracesNextLineShifted() { doTest(); }

  @Test
  public void testBracesNextLineShifted2() { doTest(); }

  @Test
  public void testBracesEndLine() { doTest(); }

  @Test
  public void testArrayInitializerSpaces() { doTest(); }

  @Test
  public void testArrayInitializerDontWrap() { doTest(); }

  @Test
  public void testArrayInitializerWrapAlways() { doTest(); }

  @Test
  public void testArrayInitializerWrapAlwaysAlign() { doTest(); }

  @Test
  public void testArrayInitializerWrapAlwaysNl() { doTest(); }

  @Test
  public void testLabelIndentAbsolute() { doTest(); }

  @Test
  public void testLabelIndentRelative() { doTest(); }

  @Test
  public void testLabelIndentRelativeReverse() { doTest(); }

  @Test
  public void testBlankLinesInCode() { doTest(); }

  @Test
  public void testAlignFields0BlankLines() { doTest(); }

  @Test
  public void testAlignFields1BlankLine() { doTest(); }

  @Test
  public void testAlignFields2BlankLines() { doTest(); }

  @Test
  public void testBooleanOperators() { doTest(); }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = formatter.get().getFixture();
    formatter.get().doTest(myFixture.getTestName(true) + ".test");
  }
}
