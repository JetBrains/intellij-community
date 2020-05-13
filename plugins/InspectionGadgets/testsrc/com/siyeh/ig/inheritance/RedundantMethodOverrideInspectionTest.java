// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.RecursionManager;
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
}
