// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.switchbranches;

import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.psiutils.CreateSwitchBranchesUtil;
import com.siyeh.ipp.IPPTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class CreateMissingSwitchBranchesActionTest extends IPPTestCase {

  public void testDataflowInt() {
    doTest(CreateSwitchBranchesUtil.getActionName(Arrays.asList("1", "3", "5", "7", "9")));
  }
  
  public void testDataflowLong() {
    assertIntentionNotAvailable(CreateSwitchBranchesUtil.getActionName(Arrays.asList("1", "2")));
  }

  public void testDataflowIntMixedTypes() {
    doTest(CreateSwitchBranchesUtil.getActionName(Arrays.asList("0", "1", "3", "4", "6", "7", "9")));
  }

  public void testDataflowChar() {
    doTest(CreateSwitchBranchesUtil.getActionName(Arrays.asList("a", "c", "d", "e", "h", "i", "j", "k", "l", "m", "n")));
  }

  public void testDataflowString() {
    doTest(CreateSwitchBranchesUtil.getActionName(Arrays.asList("\"bar\"", "\"baz\"", "\"foo\"")));
  }

  public void testMagicInt() {
    doTest(CreateSwitchBranchesUtil.getActionName(Arrays.asList("VAL1", "VAL3")));
  }

  public void testMagicIntMethodCall() {
    doTest(CreateSwitchBranchesUtil.getActionName(Arrays.asList("TWO", "-1")));
  }

  public void testMagicIntFlags() {
    assertIntentionNotAvailable(CreateSwitchBranchesUtil.getActionName(Arrays.asList("VAL1", "VAL3")));
  }

  public void testMagicString() {
    doTest(CreateSwitchBranchesUtil.getActionName(Arrays.asList("FOO", "BAZ")));
  }

  @Override
  protected String getRelativePath() {
    return "switchbranches";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
