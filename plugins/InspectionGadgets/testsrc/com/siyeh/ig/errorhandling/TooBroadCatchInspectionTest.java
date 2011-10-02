package com.siyeh.ig.errorhandling;

import com.siyeh.ig.IGInspectionTestCase;

public class TooBroadCatchInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/errorhandling/toobroadcatch", new TooBroadCatchInspection());
  }
}