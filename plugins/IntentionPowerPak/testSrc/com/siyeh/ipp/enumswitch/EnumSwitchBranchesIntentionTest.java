// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.enumswitch;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see CreateEnumSwitchBranchesIntention
 */
public class EnumSwitchBranchesIntentionTest extends IPPTestCase {

  public void testWithoutBraces() { doTest(); }
  public void testBeforeDefault() { doTest(); }
  public void testBeforeFallthrough() { doTest(); }
  public void testMultiple() { doTest(); }
  public void testNoActionAfterBraces() { assertIntentionNotAvailable(); }
  public void testNotAvailable() { assertIntentionNotAvailable(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("create.enum.switch.branches.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "enumswitch";
  }
}
