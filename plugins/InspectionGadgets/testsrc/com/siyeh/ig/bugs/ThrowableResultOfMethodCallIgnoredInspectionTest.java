package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

public class ThrowableResultOfMethodCallIgnoredInspectionTest extends LightInspectionTestCase {

  public void testThrowableResultOfMethodCallIgnored() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new ThrowableNotThrownInspection();
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/bugs/throwable_result_of_method_call_ignored";
  }
}
