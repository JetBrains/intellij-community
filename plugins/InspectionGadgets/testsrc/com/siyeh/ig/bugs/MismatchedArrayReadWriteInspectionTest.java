package com.siyeh.ig.bugs;

import com.siyeh.ig.IGInspectionTestCase;

public class MismatchedArrayReadWriteInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/bugs/mismatched_array_read_write",
           new MismatchedArrayReadWriteInspection());
  }
}