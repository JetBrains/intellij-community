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
    return new ThrowableInstanceNeverThrownInspection();
  }
}
