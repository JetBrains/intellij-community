// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SwitchStatementsWithoutDefaultInspectionTest extends LightJavaInspectionTestCase {

  private final SwitchStatementsWithoutDefaultInspection myInspection = new SwitchStatementsWithoutDefaultInspection();

  public void testIgnoreExhaustiveSwitchStatementsTrue() {
    myInspection.m_ignoreFullyCoveredEnums = true;
    doTest();
  }

  public void testIgnoreExhaustiveSwitchStatementsFalse() {
    myInspection.m_ignoreFullyCoveredEnums = false;
    doTest();
  }
  
  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return myInspection;
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }
}