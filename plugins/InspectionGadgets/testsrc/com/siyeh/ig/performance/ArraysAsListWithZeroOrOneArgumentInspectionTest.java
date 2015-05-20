package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ArraysAsListWithZeroOrOneArgumentInspectionTest extends LightInspectionTestCase {

  public void testArraysAsListWithZeroOrOneArgument() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ArraysAsListWithZeroOrOneArgumentInspection();
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/performance/arrays_as_list_with_one_argument";
  }
}