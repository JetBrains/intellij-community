package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class EqualsCalledonEnumConstantTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/style/equals_called_on_enum_constant",
           new EqualsCalledOnEnumConstantInspection());
  }
}