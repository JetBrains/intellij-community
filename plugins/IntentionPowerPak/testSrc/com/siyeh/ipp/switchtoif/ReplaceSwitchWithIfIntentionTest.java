// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.switchtoif;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.psi.PsiKeyword;
import com.siyeh.ipp.IPPTestCase;

public class ReplaceSwitchWithIfIntentionTest extends IPPTestCase {

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

  public void testDefaultOnly() {
    assertIntentionNotAvailable();
  }

  @Override
  protected String getIntentionName() {
    return CommonQuickFixBundle.message("fix.replace.x.with.y", PsiKeyword.SWITCH, PsiKeyword.IF);
  }

  @Override
  protected String getRelativePath() {
    return "switchtoif/replaceSwitchToIf";
  }
}
