// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.expression;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see ExpandArithmeticBracketsIntention
 */
public class ExpandArithmeticBracketsIntentionTest extends IPPTestCase {

  public void testDistribute() {
    doTest();
  }

  public void testNegation() {
    doTest();
  }

  public void testUselessPrefixes() {
    doTest();
  }

  public void testAlternateSign() {
    doTest();
  }

  public void testUnknownPrefix() {
    assertIntentionNotAvailable();
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("expand.arithmetic.brackets.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "expression/expand_arithmetic_brackets";
  }
}
