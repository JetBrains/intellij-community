package com.siyeh.ig.threading;

import com.siyeh.ig.IGInspectionTestCase;

public class SynchronizedMethodInspectionTest extends IGInspectionTestCase {

  public void test() throws Exception {
    final SynchronizedMethodInspection tool = new SynchronizedMethodInspection();
    tool.m_includeNativeMethods = false;
    tool.ignoreSynchronizedSuperMethods = true;
    doTest("com/siyeh/igtest/threading/synchronized_method", tool);
  }
}