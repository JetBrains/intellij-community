package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

public class FieldMayBeFinalInspectionTest extends LightInspectionTestCase {

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