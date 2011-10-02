package com.siyeh.ig.classlayout;

import com.siyeh.ig.IGInspectionTestCase;

public class SingletonInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/classlayout/singleton",
           new SingletonInspection());
  }
}