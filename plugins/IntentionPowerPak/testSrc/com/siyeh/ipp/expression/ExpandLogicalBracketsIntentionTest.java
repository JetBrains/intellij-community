// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.expression;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see ExpandLogicalBracketsIntention
 */
public class ExpandLogicalBracketsIntentionTest extends IPPTestCase {

  public void testDistribute() {
    doTest();
  }

  public void testUnknownPrefix() {
    doTest();
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("expand.logical.brackets.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "expression/expand_logical_brackets";
  }
}
