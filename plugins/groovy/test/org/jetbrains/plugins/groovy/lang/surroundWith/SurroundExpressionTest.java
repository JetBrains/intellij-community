// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class SurroundExpressionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "groovy/surround/expr/");

  @Test
  public void testBrackets1() { SurroundTestUtil.doTest(codeInsightFixture.get(), new ParenthesisExprSurrounder()); }

  @Test
  public void testIf1() { SurroundTestUtil.doTest(codeInsightFixture.get(), new IfExprSurrounder()); }

  @Test
  public void testIf_else1() { SurroundTestUtil.doTest(codeInsightFixture.get(), new IfElseExprSurrounder()); }

  @Test
  public void testType_cast1() { SurroundTestUtil.doTest(codeInsightFixture.get(), new TypeCastSurrounder()); }

  @Test
  public void testType_cast2() { SurroundTestUtil.doTest(codeInsightFixture.get(), new TypeCastSurrounder()); }

  @Test
  public void testWhile1() { SurroundTestUtil.doTest(codeInsightFixture.get(), new WhileExprSurrounder()); }

  @Test
  public void testWith2() { SurroundTestUtil.doTest(codeInsightFixture.get(), new WithExprSurrounder()); }

  @Test
  public void testBinaryWithCast() { SurroundTestUtil.doTest(codeInsightFixture.get(), new TypeCastSurrounder()); }

  @Test
  public void testCommandArgList() { SurroundTestUtil.doTest(codeInsightFixture.get(), new TypeCastSurrounder()); }

  @Test
  public void testCommandArgList2() { SurroundTestUtil.doTest(codeInsightFixture.get(), new TypeCastSurrounder()); }

  @Test
  public void testCommandArgList3() { SurroundTestUtil.doTest(codeInsightFixture.get(), new TypeCastSurrounder()); }

  @Test
  public void testCommandArgList4() { SurroundTestUtil.doTest(codeInsightFixture.get(), new TypeCastSurrounder()); }

  @Test
  public void testNotAndParenthesesSurrounder() { SurroundTestUtil.doTest(codeInsightFixture.get(), new NotAndParenthesesSurrounder()); }

  @Test
  public void testNotAndParenthesesOnCommandExpr() { SurroundTestUtil.doTest(codeInsightFixture.get(), new NotAndParenthesesSurrounder()); }
}
