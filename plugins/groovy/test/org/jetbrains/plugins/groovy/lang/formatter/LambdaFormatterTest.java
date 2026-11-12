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
public class LambdaFormatterTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GroovyFormatterFixture> formatter =
    groovyFormatterFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "groovy/formatter/lambda/");

  @Test
  public void testBraceStyle1() { doTest(); }

  @Test
  public void testBraceStyle2() { doTest(); }

  @Test
  public void testBraceStyle3() { doTest(); }

  @Test
  public void testBraceStyle4() { doTest(); }

  @Test
  public void testBraceStyle5() { doTest(); }

  @Test
  public void testBraceStyle6() { doTest(); }

  @Test
  public void testBraceStyle7() { doTest(); }

  @Test
  public void testBraceStyle8() { doTest(); }

  @Test
  public void testOneLineLambda1() { doTest(); }

  @Test
  public void testOneLineLambda2() { doTest(); }

  @Test
  public void testOneLineLambda3() { doTest(); }

  @Test
  public void testOneLineLambda4() { doTest(); }

  @Test
  public void testOneLineLambda5() { doTest(); }

  @Test
  public void testOneLineLambda6() { doTest(); }

  @Test
  public void testOneLineLambda7() { doTest(); }

  @Test
  public void testParams() { doTest(); }

  @Test
  public void testLambdaParametersAligned() { doTest(); }

  @Test
  public void testAlignLambdaBraceWithCall() { doTest(); }

  @Test
  public void testChainCall1() { doTest(); }

  @Test
  public void testChainCall2() { doTest(); }

  @Test
  public void testChainCall3() { doTest(); }

  @Test
  public void testChainCall4() { doTest(); }

  @Test
  public void testChainCall5() { doTest(); }

  @Test
  public void testChainCall6() { doTest(); }

  @Test
  public void testChainCall7() { doTest(); }

  @Test
  public void testChainCall8() { doTest(); }

  @Test
  public void testChainCall9() { doTest(); }

  @Test
  public void testChainCall10() { doTest(); }

  @Test
  public void testChainCallWithSingleExpressionLambda1() { doTest(); }

  @Test
  public void testChainCallWithSingleExpressionLambda2() { doTest(); }

  @Test
  public void testNoFlyingGeese() { doTest(); }

  @Test
  public void testNoFlyingGeese2() { doTest(); }

  @Test
  public void testSpacesAroundArrow1() { doTest(); }

  @Test
  public void testSpacesAroundArrow2() { doTest(); }

  @Test
  public void testSpacesAroundArrow3() { doTest(); }

  @Test
  public void testSpacesAroundArrow4() { doTest(); }

  @Test
  public void testSpacesAroundArrow5() { doTest(); }

  @Test
  public void testSpacesAroundArrow6() { doTest(); }

  @Test
  public void testSpacesAroundArrow7() { doTest(); }

  @Test
  public void testSpacesAroundArrow8() { doTest(); }

  @Test
  public void testSpacesAroundArrow9() { doTest(); }

  @Test
  public void testSpacesAroundArrow10() { doTest(); }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = formatter.get().getFixture();
    formatter.get().doTest(myFixture.getTestName(true) + ".test");
  }
}
