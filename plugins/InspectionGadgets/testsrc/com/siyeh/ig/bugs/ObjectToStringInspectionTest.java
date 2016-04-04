package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ObjectToStringInspectionTest extends LightInspectionTestCase {

  public void testObjectToString() throws Exception {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ObjectToStringInspection();
  }
}