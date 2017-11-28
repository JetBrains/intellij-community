package com.siyeh.ig.naming;

import com.siyeh.ig.IGInspectionTestCase;

public class MethodNamesDifferOnlyByCaseInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/naming/method_names_differ_only_by_case", new MisspelledMethodNameInspection());
  }
}