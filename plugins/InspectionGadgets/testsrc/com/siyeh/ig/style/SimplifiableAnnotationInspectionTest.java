package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class SimplifiableAnnotationInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/style/simplifiable_annotation",
           new SimplifiableAnnotationInspection());
  }
}