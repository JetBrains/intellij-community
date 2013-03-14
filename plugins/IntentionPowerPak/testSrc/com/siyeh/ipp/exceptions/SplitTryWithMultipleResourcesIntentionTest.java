package com.siyeh.ipp.exceptions;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class SplitTryWithMultipleResourcesIntentionTest extends IPPTestCase {

  public void testSimple() { doTest(); }
  public void testWithCatch() { doTest(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("split.try.with.multiple.resources.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "exceptions/splitTry";
  }
}
