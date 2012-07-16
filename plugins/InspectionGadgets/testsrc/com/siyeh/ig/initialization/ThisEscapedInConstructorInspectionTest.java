package com.siyeh.ig.initialization;

import com.siyeh.ig.IGInspectionTestCase;

public class ThisEscapedInConstructorInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    doTest("com/siyeh/igtest/initialization/this_escaped_in_constructor", new ThisEscapedInConstructorInspection());
  }
}