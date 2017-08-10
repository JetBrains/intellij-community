package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class ImplicitArrayToStringInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/bugs/implicit_array_to_string",
           new ImplicitArrayToStringInspection());
  }
}