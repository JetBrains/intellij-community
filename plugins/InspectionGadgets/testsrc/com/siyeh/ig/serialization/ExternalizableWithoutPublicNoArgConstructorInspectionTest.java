package com.siyeh.ig.serialization;

import com.siyeh.ig.IGInspectionTestCase;

public class ExternalizableWithoutPublicNoArgConstructorInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/serialization/externalizable_without_public_no_arg_constructor",
           new ExternalizableWithoutPublicNoArgConstructorInspection());
  }
}