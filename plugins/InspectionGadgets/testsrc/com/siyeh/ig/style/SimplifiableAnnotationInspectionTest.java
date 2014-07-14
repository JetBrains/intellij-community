package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

public class SimplifiableAnnotationInspectionTest extends LightInspectionTestCase {

  public void testSimplifiableAnnotation() throws Exception {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new SimplifiableAnnotationInspection();
  }
}