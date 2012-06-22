package com.siyeh.ipp.exceptions;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class MergeNestedTryStatementsIntentionTest extends IPPTestCase {

  public void testSimple() { doTest(); }
  public void testWithoutAndWithResources() { doTest(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("merge.nested.try.statements.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "exceptions/mergeTry";
  }
}
