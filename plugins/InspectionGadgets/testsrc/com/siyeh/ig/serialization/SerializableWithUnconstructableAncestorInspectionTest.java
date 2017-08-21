package com.siyeh.ig.serialization;

import com.siyeh.ig.IGInspectionTestCase;

public class SerializableWithUnconstructableAncestorInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/serialization/serializable_with_unconstructable_ancestor",
           new SerializableWithUnconstructableAncestorInspection());
  }
}