package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class PrimitiveArrayArgumentToVariableArgMethodInspectionTest extends LightInspectionTestCase {

  public void testPrimitiveArrayArgumentToVariableArgMethod() throws Exception {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new PrimitiveArrayArgumentToVariableArgMethodInspection();
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/bugs/var_arg";
  }
}