package com.siyeh.ig.inheritance;

import com.siyeh.ig.IGInspectionTestCase;

public class TypeParameterExtendsFinalClassInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/inheritance/type_parameter_extends_final_class", new TypeParameterExtendsFinalClassInspection());
  }
}
