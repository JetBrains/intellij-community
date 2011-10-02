package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class ObjectToStringInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/bugs/object_to_string",
           new ObjectToStringInspection());
  }
}