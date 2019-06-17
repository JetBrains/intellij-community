package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class SimplifiableAnnotationInspectionTest extends LightJavaInspectionTestCase {

  public void testSimplifiableAnnotation() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new SimplifiableAnnotationInspection();
  }
}