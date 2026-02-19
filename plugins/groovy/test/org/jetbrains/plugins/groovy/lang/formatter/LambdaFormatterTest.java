// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.formatter;

import org.jetbrains.plugins.groovy.util.TestUtils;

public class LambdaFormatterTest extends GroovyFormatterTestCase {
  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/formatter/lambda/";
  }

  public void testBraceStyle1() throws Throwable { doTest(); }

  public void testBraceStyle2() throws Throwable { doTest(); }

  public void testBraceStyle3() throws Throwable { doTest(); }

  public void testBraceStyle4() throws Throwable { doTest(); }

  public void testBraceStyle5() throws Throwable { doTest(); }

  public void testBraceStyle6() throws Throwable { doTest(); }

  public void testBraceStyle7() throws Throwable { doTest(); }

  public void testBraceStyle8() throws Throwable { doTest(); }

  public void testOneLineLambda1() throws Throwable { doTest(); }

  public void testOneLineLambda2() throws Throwable { doTest(); }

  public void testOneLineLambda3() throws Throwable { doTest(); }

  public void testOneLineLambda4() throws Throwable { doTest(); }

  public void testOneLineLambda5() throws Throwable { doTest(); }

  public void testOneLineLambda6() throws Throwable { doTest(); }

  public void testOneLineLambda7() throws Throwable { doTest(); }

  public void testParams() throws Throwable { doTest(); }

  public void testLambdaParametersAligned() throws Throwable { doTest(); }

  public void testAlignLambdaBraceWithCall() throws Throwable { doTest(); }

  public void testChainCall1() throws Throwable { doTest(); }

  public void testChainCall2() throws Throwable { doTest(); }

  public void testChainCall3() throws Throwable { doTest(); }

  public void testChainCall4() throws Throwable { doTest(); }

  public void testChainCall5() throws Throwable { doTest(); }

  public void testChainCall6() throws Throwable { doTest(); }

  public void testChainCall7() throws Throwable { doTest(); }

  public void testChainCall8() throws Throwable { doTest(); }

  public void testChainCall9() throws Throwable { doTest(); }

  public void testChainCall10() throws Throwable { doTest(); }

  public void testChainCallWithSingleExpressionLambda1() throws Throwable { doTest(); }

  public void testChainCallWithSingleExpressionLambda2() throws Throwable { doTest(); }

  public void testNoFlyingGeese() throws Throwable { doTest(); }

  public void testNoFlyingGeese2() throws Throwable { doTest(); }

  public void testSpacesAroundArrow1() throws Throwable { doTest(); }

  public void testSpacesAroundArrow2() throws Throwable { doTest(); }

  public void testSpacesAroundArrow3() throws Throwable { doTest(); }

  public void testSpacesAroundArrow4() throws Throwable { doTest(); }

  public void testSpacesAroundArrow5() throws Throwable { doTest(); }

  public void testSpacesAroundArrow6() throws Throwable { doTest(); }

  public void testSpacesAroundArrow7() throws Throwable { doTest(); }

  public void testSpacesAroundArrow8() throws Throwable { doTest(); }

  public void testSpacesAroundArrow9() throws Throwable { doTest(); }

  public void testSpacesAroundArrow10() throws Throwable { doTest(); }

  private void doTest() throws Throwable {
    doTest(getTestName(true) + ".test");
  }
}
