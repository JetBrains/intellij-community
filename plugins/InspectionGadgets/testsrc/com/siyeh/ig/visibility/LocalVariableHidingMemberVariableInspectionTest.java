// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.visibility;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class LocalVariableHidingMemberVariableInspectionTest extends LightJavaInspectionTestCase {

  public void testLocalVariableHidingMemberVariable() {
    doTest();
  }

  public void testLocalVariableHidingMemberVariableReportAllFields() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final LocalVariableHidingMemberVariableInspection inspection = new LocalVariableHidingMemberVariableInspection();
    if (getTestName(false).endsWith("ReportAllFields")) {
      inspection.m_ignoreInvisibleFields = false;
      inspection.m_ignoreStaticMethods = false;
    }
    return inspection;
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      """
package com.siyeh.igtest.visibility2;
public class DifferentPackageClass
{
    int fooBar;
    protected int fooBar2;
}
"""
    };
  }
}