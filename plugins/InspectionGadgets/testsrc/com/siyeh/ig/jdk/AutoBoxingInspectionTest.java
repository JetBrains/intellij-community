package com.siyeh.ig.jdk;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class AutoBoxingInspectionTest extends LightInspectionTestCase {

  public void testAutoBoxing() throws Exception {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new AutoBoxingInspection();
  }
}