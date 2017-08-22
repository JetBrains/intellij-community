package com.intellij.codeInspection;

import com.intellij.codeInspection.booleanIsAlwaysInverted.BooleanMethodIsAlwaysInvertedInspection;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.siyeh.ig.IGInspectionTestCase;

public class BooleanMethodInvertedTest extends IGInspectionTestCase {

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
    final LanguageLevelProjectExtension projectExtension = LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject());
    final LanguageLevel oldLevel = projectExtension.getLanguageLevel();
    try {
      projectExtension.setLanguageLevel(LanguageLevel.JDK_1_8);
      doTest();
    }
    finally {
      projectExtension.setLanguageLevel(oldLevel);
    }
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(boolean checkRange) {
    doTest("invertedBoolean/" + getTestName(true), new BooleanMethodIsAlwaysInvertedInspection(), checkRange);
  }
}
