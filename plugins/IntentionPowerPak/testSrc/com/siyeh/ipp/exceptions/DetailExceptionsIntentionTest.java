package com.siyeh.ipp.exceptions;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class DetailExceptionsIntentionTest extends IPPTestCase {

  public void testDisjunction() { assertIntentionNotAvailable(); }
  public void testSimple() { doTest(); }
  public void testForeach() { doTest(); }
  public void testTryWithResources() { doTest(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("detail.exceptions.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "exceptions/detail";
  }
}
