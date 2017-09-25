package com.siyeh.ig.classlayout;

import com.siyeh.ig.IGInspectionTestCase;

public class MarkerInterfaceInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/classlayout/marker_interface", new MarkerInterfaceInspection());
  }
}