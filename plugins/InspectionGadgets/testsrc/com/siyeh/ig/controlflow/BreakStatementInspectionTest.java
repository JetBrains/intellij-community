// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class BreakStatementInspectionTest extends LightInspectionTestCase {

  public void testSwitchExpression() {
    doStatementTest("int i = switch (1) { case 1: break 1; default: break 2; };");
  }

  public void testSwitchStatement() {
    doStatementTest("switch (1) { case 1: {{ break; }} }");
  }

  public void testWhileLoop() {
    doStatementTest("while (true) { if (1 == 1) /*'break' statement*/break/**/; }");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new BreakStatementInspection();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_12;
  }
}