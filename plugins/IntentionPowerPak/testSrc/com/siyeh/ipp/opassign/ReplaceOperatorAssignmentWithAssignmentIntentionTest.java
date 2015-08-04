package com.siyeh.ipp.opassign;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see ReplaceOperatorAssignmentWithAssignmentIntention
 */
public class ReplaceOperatorAssignmentWithAssignmentIntentionTest extends IPPTestCase {
  public void testOperatorAssignment1() { doTest(); }
  public void testDoubleOpAssign() { doTest(); }
  public void testStringOpAssign() { doTest(); }
  public void testByteOpAssign() { doTest(); }
  public void testPrecedence() { doTest(); }
  public void testPolyadicAssignment() { doTest(IntentionPowerPackBundle.message("replace.operator.assignment.with.assignment.intention.name", "*=")); }
  public void testConditionalAssignment() { doTest(IntentionPowerPackBundle.message("replace.operator.assignment.with.assignment.intention.name", "*=")); }
  public void testIncomplete() { doTest(); }
  public void testCastNecessary() { doTest(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("replace.operator.assignment.with.assignment.intention.name", "+=");
  }

  @Override
  protected String getRelativePath() {
    return "opassign/assignment";
  }
}
