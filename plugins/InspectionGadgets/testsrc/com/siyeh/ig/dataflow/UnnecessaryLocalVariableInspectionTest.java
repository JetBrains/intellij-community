// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.dataflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryLocalVariableInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessaryLocalVariableInspection();
  }

  public void testC() { doTest(); }

  public void testTree() { doTest(); }

  public void testSwitchExpression() { doTest(); }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_13;
  }
}