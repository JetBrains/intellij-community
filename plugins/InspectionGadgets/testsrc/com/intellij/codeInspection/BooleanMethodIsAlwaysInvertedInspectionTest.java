// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.booleanIsAlwaysInverted.BooleanMethodIsAlwaysInvertedInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.ig.IGInspectionTestCase;

public class BooleanMethodIsAlwaysInvertedInspectionTest extends IGInspectionTestCase {

  public void testUnusedMethod() {
    doTest();
  }

  public void testNotAlwaysInverted() {
    doTest();
  }

  public void testAlwaysInverted() {
    doTest();
  }

  public void testAlwaysInvertedDelegation() {
    doTest();
  }

  public void testAlwaysInvertedOneUsage() {
    doTest();
  }

  public void testAlwaysInvertedByRange() {
    doTest(true);
  }

  public void testFromExpression() {
    doTest();
  }

  public void testAlwaysInvertedInScope() {
    doTest();
  }

  public void testHierarchyNotAlwaysInverted() {
    doTest();
  }

  public void testDeepHierarchyNotAlwaysInverted() {
    doTest();
  }

  public void testDeepHierarchyNotAlwaysInvertedInScope() {
    doTest();
  }

  public void testDeepHierarchyAlwaysInverted() {
    doTest();
  }

  public void testOverrideLibrary() {
    doTest();
  }

  public void testMethodReferenceIgnored() {
    IdeaTestUtil.withLevel(myModule, LanguageLevel.JDK_1_8, () -> doTest());
  }

  private void doTest() {
    doTest(false);
  }

  protected void doTest(boolean checkRange) {
    doTest("invertedBoolean/" + getTestName(true), new BooleanMethodIsAlwaysInvertedInspection(), checkRange);
  }
}
