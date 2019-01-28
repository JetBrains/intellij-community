// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NestedSwitchInspectionTest extends LightInspectionTestCase {

  public void testNestedSwitch() {
    doTest();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LightCodeInsightFixtureTestCase.JAVA_12;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new NestedSwitchStatementInspection();
  }
}
