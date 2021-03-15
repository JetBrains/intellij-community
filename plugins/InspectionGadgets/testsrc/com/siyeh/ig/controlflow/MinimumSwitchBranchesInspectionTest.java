// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class MinimumSwitchBranchesInspectionTest extends LightJavaInspectionTestCase {

  public void testMinimumSwitchBranches() {
    doTest();
  }


  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new MinimumSwitchBranchesInspection();
  }
}
