package com.siyeh.ig.threading;

import com.siyeh.ig.IGInspectionTestCase;

public class SynchronizedOnLiteralObjectInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/threading/synchronized_on_literal_object", new SynchronizedOnLiteralObjectInspection());
  }
}