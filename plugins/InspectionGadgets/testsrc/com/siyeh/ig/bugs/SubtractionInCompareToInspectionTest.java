package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.siyeh.ig.IGInspectionTestCase;

public class SubtractionInCompareToInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    super.doTest("com/siyeh/igtest/bugs/subtraction_in_compare_to", new LocalInspectionToolWrapper(new SubtractionInCompareToInspection()), "java 1.8");
  }
}