/*
 * Copyright (c) 2006 Your Corporation. All Rights Reserved.
 */
package com.intellij.codeInspection;

import com.IGInspectionTestCase;
import com.intellij.codeInspection.booleanIsAlwaysInverted.BooleanMethodIsAlwaysInvertedInspection;

/**
 * User: anna
 * Date: 06-Jan-2006
 */
public class BooleanMethodInvertedTest extends IGInspectionTestCase {

  public void testUnusedMethod() throws Exception {
    doTest();
  }

  public void testNotAlwaysInverted() throws Exception{
    doTest();
  }

  public void testAlwaysInverted() throws Exception{
    doTest();
  }

  public void testAlwaysInvertedByRange() throws Exception{
    doTest(true);
  }

  public void testFromExpression() throws Exception{
    doTest();
  }

  public void testAlwaysInvertedInScope() throws Exception{
    doTest();
  }

  public void testHierarchyNotAlwaysInverted() throws Exception {
    doTest();
  }

  public void testDeepHierarchyNotAlwaysInverted() throws Exception {
    doTest();
  }

  public void testDeepHierarchyNotAlwaysInvertedInScope() throws Exception {
    doTest();
  }

  public void testDeepHierarchyAlwaysInverted() throws Exception {
    doTest();
  }

  public void testOverrideLibrary() throws Exception{
    doTest();
  }

  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(boolean checkRange) throws Exception {
    doTest("invertedBoolean/" + getTestName(true), new BooleanMethodIsAlwaysInvertedInspection(), checkRange);
  }
}
