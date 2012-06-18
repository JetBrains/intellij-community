package com.siyeh.ipp.concatenation;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class ReplaceConcatenationWithStringBufferIntentionTest extends IPPTestCase {

  public void testNonStringConcatenationStart() { doTest(); }
  public void testConcatenationInsideAppend() { doTest(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("replace.concatenation.with.string.builder.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "concatenation/string_builder";
  }
}
