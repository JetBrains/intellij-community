package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.NotNull;

public class IfStatementWithIdenticalBranchesInspectionTest extends LightQuickFixParameterizedTestCase {

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    IfStatementWithIdenticalBranchesInspection inspection = new IfStatementWithIdenticalBranchesInspection();
    return new LocalInspectionTool[]{inspection};
  }

  public void test() {
    doAllTests();
  }

  @Override
  protected String getBasePath() {
    return "/inspection/commonIfParts";
  }
}