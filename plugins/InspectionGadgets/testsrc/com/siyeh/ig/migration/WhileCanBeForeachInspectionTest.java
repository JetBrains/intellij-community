package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class WhileCanBeForeachInspectionTest extends LightInspectionTestCase {

  public void testWhileCanBeForeach() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new WhileCanBeForeachInspection();
  }
}
