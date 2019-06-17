package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class FieldMayBeFinalInspectionTest extends LightJavaInspectionTestCase {

  public void testFieldMayBeFinal() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new FieldMayBeFinalInspection();
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/style/field_final";
  }
}