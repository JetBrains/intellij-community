package com.siyeh.ipp.unicode;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see com.siyeh.ipp.unicode.UnicodeUnescapeIntention
 */
public class UnicodeUnescapeIntentionTest extends IPPTestCase {

  public void testSimple() { doTest(); }
  public void testSelection() { doTest(); }
  public void testNoException() { assertIntentionNotAvailable(); }
  public void testU() { assertIntentionNotAvailable(); }

  @Override
  protected String getRelativePath() {
    return "unicode/unescape";
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("unicode.unescape.intention.name");
  }
}