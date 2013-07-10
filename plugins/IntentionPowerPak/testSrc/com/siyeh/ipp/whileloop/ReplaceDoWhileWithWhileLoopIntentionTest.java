package com.siyeh.ipp.whileloop;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

public class ReplaceDoWhileWithWhileLoopIntentionTest extends IPPTestCase {

  public void testWithoutBraces() { doTest(); }
  public void testFinalVariable1() { doTest(); }
  public void testFinalVariable2() { doTest(); }

  @Override
  protected String getRelativePath() {
    return "whileloop/replace_do_while_with_while_loop";
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("replace.do.while.loop.with.while.loop.intention.name");
  }
}