// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;

public class ConstantConditionalExpressionInspectionFixTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new ConstantConditionalExpressionInspection());
  }

  public void test() { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/constantConditional";
  }
}
