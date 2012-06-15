
package com.siyeh.ipp.concatenation;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class MergeCallSequenceToChainIntentionTest extends IPPTestCase {

  public void testAppend() { doTest(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("merge.call.sequence.to.chain.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "concatenation/merge_sequence";
  }
}
