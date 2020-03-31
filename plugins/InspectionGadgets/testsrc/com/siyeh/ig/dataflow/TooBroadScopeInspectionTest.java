package com.siyeh.ig.dataflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class TooBroadScopeInspectionTest extends LightJavaInspectionTestCase {

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