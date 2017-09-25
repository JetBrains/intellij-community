package com.siyeh.ig.j2me;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

public class SimplifiableIfStatementInspectionTest extends LightInspectionTestCase {

  public void testSimplifiableIfStatement() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new SimplifiableIfStatementInspection();
  }
}