// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @see SuspiciousIntegerDivAssignmentInspection
 */
public class SuspiciousIntegerDivAssignmentTest extends LightInspectionTestCase {

  public void testSuspiciousIntegerDivAssignment() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new SuspiciousIntegerDivAssignmentInspection();
  }
}
