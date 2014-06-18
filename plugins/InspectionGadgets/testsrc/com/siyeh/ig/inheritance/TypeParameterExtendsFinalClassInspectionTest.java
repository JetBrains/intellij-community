package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;

/**
 * @author bas
 */
public class TypeParameterExtendsFinalClassInspectionTest extends LightInspectionTestCase {

  public void testTypeParameterExtendsFinalClass() throws Exception {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new TypeParameterExtendsFinalClassInspection();
  }
}
