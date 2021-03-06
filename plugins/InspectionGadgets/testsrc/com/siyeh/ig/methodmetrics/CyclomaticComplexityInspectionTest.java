// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.methodmetrics;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class CyclomaticComplexityInspectionTest extends LightJavaInspectionTestCase {

  public void testCyclomaticComplexity() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final CyclomaticComplexityInspection inspection = new CyclomaticComplexityInspection();
    inspection.m_limit = 1;
    return inspection;
  }
}
