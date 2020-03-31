// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class SimplifiableIfStatementInspectionTest extends LightJavaInspectionTestCase {
  public void testSimplifiableIfStatement() {
    doTest();
  }
  public void testIfMayBeConditional() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    SimplifiableIfStatementInspection inspection = new SimplifiableIfStatementInspection();
    inspection.DONT_WARN_ON_TERNARY = false;
    return inspection;
  }

}