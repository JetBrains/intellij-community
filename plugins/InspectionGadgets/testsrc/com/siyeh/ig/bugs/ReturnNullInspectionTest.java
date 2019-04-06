// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ReturnNullInspectionTest extends LightInspectionTestCase {

  public void testReturnNull() {
    doTest();
  }

  public void testWarnOptional() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final ReturnNullInspection inspection = new ReturnNullInspection();
    inspection.m_reportObjectMethods = !"WarnOptional".equals(getTestName(false));
    inspection.m_ignorePrivateMethods = "WarnOptional".equals(getTestName(false));
    return inspection;
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}