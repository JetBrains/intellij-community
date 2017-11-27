// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.exceptions;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see DetailExceptionsIntention
 * @author Bas Leijdekkers
 */
public class DetailExceptionsIntentionTest extends IPPTestCase {

  public void testDisjunction() { assertIntentionNotAvailable(); }
  public void testSimple() { doTest(); }
  public void testForeach() { doTest(); }
  public void testTryWithResources() { doTest(); }
  public void testPolyadicParentheses() { doTest(); }
  public void testCaretAtParameter() { doTest(); }
  public void testCommonSuperType() { doTest(); }
  public void testCatchBody() { assertIntentionNotAvailable(); }


  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("detail.exceptions.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "exceptions/detail";
  }
}
