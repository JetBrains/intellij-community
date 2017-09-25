package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class IfStatementWithIdenticalBranchesInspectionTest extends LightInspectionTestCase {

  public void testIfStatementWithIdenticalBranches() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new IfStatementWithIdenticalBranchesInspection();
  }
}