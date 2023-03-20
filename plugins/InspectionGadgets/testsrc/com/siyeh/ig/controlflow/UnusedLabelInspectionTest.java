// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.redundancy.UnusedLabelInspection;
import org.jetbrains.annotations.Nullable;

/**
 * @author Fabrice TIERCELIN
 */
public class UnusedLabelInspectionTest extends LightJavaInspectionTestCase {

  public void testUnusedLabel() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnusedLabelInspection();
  }
}
