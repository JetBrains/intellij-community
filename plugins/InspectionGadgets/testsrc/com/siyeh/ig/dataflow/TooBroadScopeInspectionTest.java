package com.siyeh.ig.dataflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.IGInspectionTestCase;
import com.siyeh.ig.LightInspectionTestCase;

public class TooBroadScopeInspectionTest extends LightInspectionTestCase {

  public void testTooBroadScope() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new TooBroadScopeInspection();
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/dataflow/scope";
  }
}