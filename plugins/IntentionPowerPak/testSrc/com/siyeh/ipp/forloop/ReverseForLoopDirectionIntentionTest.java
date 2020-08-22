// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.forloop;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class ReverseForLoopDirectionIntentionTest extends IPPTestCase {

  public void testAssignmentUpdate() { doTest(); }
  public void testOperatorAssignment() { doTest(); }
  public void testComment() { doTest(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("reverse.for.loop.direction.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "forloop/reverse";
  }
}