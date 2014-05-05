package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

public class ForCanBeForeachInspectionTest extends LightInspectionTestCase {

  public void testForCanBeForEach() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new ForCanBeForeachInspection();
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/migration/foreach";
  }
}