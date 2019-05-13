// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;


public class FinallyBlockCannotCompleteNormallyInspectionTest extends LightInspectionTestCase {

  public void testFinallyBlockCannotCompleteNormally() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new FinallyBlockCannotCompleteNormallyInspection();
  }
}