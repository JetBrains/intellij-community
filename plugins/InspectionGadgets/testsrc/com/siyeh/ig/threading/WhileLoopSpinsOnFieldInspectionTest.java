package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class WhileLoopSpinsOnFieldInspectionTest extends LightInspectionTestCase {

  public void testWhileLoopSpinsOnField() throws Exception {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new WhileLoopSpinsOnFieldInspection();
  }
}