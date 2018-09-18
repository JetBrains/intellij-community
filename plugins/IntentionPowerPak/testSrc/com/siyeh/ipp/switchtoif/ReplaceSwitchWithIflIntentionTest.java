// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.switchtoif;

import com.siyeh.ipp.IPPTestCase;

public class ReplaceSwitchWithIflIntentionTest extends IPPTestCase {

  public void testReplaceInt() {
    doTest();
  }

  public void testReplaceInteger() {
    doTest();
  }

  public void testReplaceChar() {
    doTest();
  }

  public void testReplaceCharacter() {
    doTest();
  }

  @Override
  protected String getIntentionName() {
    return "Replace 'switch' with 'if'";
  }

  @Override
  protected String getRelativePath() {
    return "switchtoif/replaceSwitchToIf";
  }
}
