package com.siyeh.ig.classlayout;

import com.siyeh.ig.IGInspectionTestCase;

public class ProtectedMemberInFinalClassInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/classlayout/protected_member_in_final_class", new ProtectedMemberInFinalClassInspection());
  }
}