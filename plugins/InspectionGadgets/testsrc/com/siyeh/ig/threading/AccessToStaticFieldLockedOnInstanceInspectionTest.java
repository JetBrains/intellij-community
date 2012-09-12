package com.siyeh.ig.threading;

import com.siyeh.ig.IGInspectionTestCase;

public class AccessToStaticFieldLockedOnInstanceInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final AccessToStaticFieldLockedOnInstanceInspection tool = new AccessToStaticFieldLockedOnInstanceInspection();
    tool.ignoredClasses.add("java.util.List");
    doTest("com/siyeh/igtest/threading/access_to_static_field_locked_on_instance_data", tool);
  }
}