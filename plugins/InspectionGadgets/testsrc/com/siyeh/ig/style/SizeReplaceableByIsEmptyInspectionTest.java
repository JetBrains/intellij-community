package com.siyeh.ig.style;

import com.siyeh.ig.IGInspectionTestCase;

public class SizeReplaceableByIsEmptyInspectionTest extends IGInspectionTestCase {

  public void test() {
    doTest("com/siyeh/igtest/style/size_replaceable_by_is_empty", new SizeReplaceableByIsEmptyInspection());
  }
}
