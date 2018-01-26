/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ipp.whileloop;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see ExtractWhileLoopConditionToIfStatementIntention
 * @author Bas Leijdekkers
 */
public class ExtractWhileLoopConditionToIfStatementIntentionTest extends IPPTestCase {

  public void testSimple() { doTest(); }
  public void testNoBody() { doTest(); }

  @Override
  protected String getRelativePath() {
    return "whileloop/extract_while_loop_condition_to_if_statement";
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("extract.while.loop.condition.to.if.statement.intention.name");
  }
}