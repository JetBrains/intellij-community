// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class RedundantMethodOverrideInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected InspectionProfileEntry getInspection() {
    final RedundantMethodOverrideInspection inspection = new RedundantMethodOverrideInspection();
    inspection.checkLibraryMethods = true;
    return inspection;
  }

  public void testRedundantMethodOverride() { doTest(); }

  public void testMutualRecursion() {
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());
    doTest();
  }

  public void testLibraryOverride() {
    myFixture.allowTreeAccessForAllFiles();
    doTest();
  }

  public void testSwitch() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_19_PREVIEW, this::doTest);
  }

  public void testInstanceOf() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_19_PREVIEW, this::doTest);
  }

  public void testGuardedAndParenthesizedPatterns() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_17_PREVIEW, this::doTest);
  }
}
