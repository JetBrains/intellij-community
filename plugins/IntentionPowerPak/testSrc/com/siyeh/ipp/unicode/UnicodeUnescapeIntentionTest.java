package com.siyeh.ipp.unicode;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

public class UnicodeUnescapeIntentionTest extends IPPTestCase {

  public void testSimple() { doTest(); }

  @Override
  protected String getRelativePath() {
    return "unicode/unescape";
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("unicode.unescape.intention.name");
  }
}