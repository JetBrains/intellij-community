package com.siyeh.ig.encapsulation;

import com.siyeh.ig.IGInspectionTestCase;

public class PackageVisibleInnerClassInspectionTest extends IGInspectionTestCase {

  public void test() {
    final PackageVisibleInnerClassInspection tool = new PackageVisibleInnerClassInspection();
    tool.ignoreEnums = true;
    tool.ignoreInterfaces = true;
    doTest("com/siyeh/igtest/encapsulation/package_visible_inner_class", tool);
  }
}
