// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.shift;

import com.siyeh.ipp.IPPTestCase;

public class ReplaceShiftWithMultiplyIntentionTest extends IPPTestCase {

  public void testLeftShift() { doTest("Replace '<<' with '*'"); }
  public void testLeftShiftAssign() { doTest("Replace '<<=' with '*='"); }
  public void testParentheses() { doTest("Replace '<<' with '*'"); }
  public void testRightShift() { doTest("Replace '>>' with '/'"); }

  @Override
  protected String getRelativePath() {
    return "shift/replace_shift_with_multiply";
  }
}
