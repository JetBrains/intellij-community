package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.siyeh.ig.IGInspectionTestCase;
import com.intellij.codeInspection.booleanIsAlwaysInverted.BooleanMethodIsAlwaysInvertedInspection;

/**
 * User: anna
 * Date: 06-Jan-2006
 */
public class BooleanMethodInvertedTest extends IGInspectionTestCase {

  public void testUnusedMethod() throws Exception {
    doTest();
  }

  public void testNotAlwaysInverted() throws Exception {
    doTest();
  }

  public void testAlwaysInverted() throws Exception {
    doTest();
  }

  public void testAlwaysInvertedDelegation() throws Exception {
    doTest();
  }

  public void testAlwaysInvertedOneUsage() throws Exception {
    doTest();
  }

  public void testAlwaysInvertedByRange() throws Exception {
    doTest(true);
  }

  public void testFromExpression() throws Exception {
    doTest();
  }

  public void testAlwaysInvertedInScope() throws Exception {
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

  public void testOverrideLibrary() throws Exception {
    doTest();
  }

  public void testMethodReferenceIgnored() throws Exception {
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

  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(boolean checkRange) throws Exception {
    doTest("invertedBoolean/" + getTestName(true), new BooleanMethodIsAlwaysInvertedInspection(), checkRange);
  }
}
