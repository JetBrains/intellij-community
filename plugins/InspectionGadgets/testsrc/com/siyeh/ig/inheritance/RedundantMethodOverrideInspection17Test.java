// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class RedundantMethodOverrideInspection17Test extends LightJavaInspectionTestCase {
  @Override
  protected InspectionProfileEntry getInspection() {
    return new RedundantMethodOverrideInspection();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_17;
  }

  public void testIdenticalSwitches() {
    doTest();
  }
}
