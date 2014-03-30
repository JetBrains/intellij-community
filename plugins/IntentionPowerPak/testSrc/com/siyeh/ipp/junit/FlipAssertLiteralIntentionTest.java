package com.siyeh.ipp.junit;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see FlipAssertLiteralIntention
 * @author Bas Leijdekkers
 */
public class FlipAssertLiteralIntentionTest extends IPPTestCase {

  public void testMessage() { doTest(); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package org.junit;" +
                       "class Assert {" +
                       "  public static void assertTrue(java.lang.String message, boolean condition) {}" +
                       "}");
  }

  @Override
  protected String getRelativePath() {
    return "junit/flip_assert_literal";
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("flip.assert.literal.intention.name", "assertTrue", "assertFalse");
  }
}
