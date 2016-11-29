package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ThrowableInstanceNeverThrownInspectionTest extends LightInspectionTestCase {

  public void testThrowableInstanceNeverThrown() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ThrowableNotThrownInspection();
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/bugs/throwable_instance_never_thrown";
  }
}
