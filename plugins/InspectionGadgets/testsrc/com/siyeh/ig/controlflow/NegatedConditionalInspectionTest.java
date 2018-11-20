// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roland Illig
 */
public class NegatedConditionalInspectionTest extends LightInspectionTestCase {

  public void testNegatedConditionalNullAndZeroAllowed() {
    doTest();
  }

  public void testNegatedConditionalNullAndZeroDisallowed() {
    doTest();
  }

  public void testNegatedConditionalNullAllowed() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final NegatedConditionalInspection inspection = new NegatedConditionalInspection();

    final String testName = getTestName(true);
    switch (testName) {
      case "negatedConditionalNullAndZeroAllowed":
        inspection.m_ignoreNegatedNullComparison = true;
        inspection.m_ignoreNegatedZeroComparison = true;
        break;
      case "negatedConditionalNullAndZeroDisallowed":
        inspection.m_ignoreNegatedNullComparison = false;
        inspection.m_ignoreNegatedZeroComparison = false;
        break;
      case "negatedConditionalNullAllowed":
        inspection.m_ignoreNegatedNullComparison = true;
        inspection.m_ignoreNegatedZeroComparison = false;
        break;
      default:
        throw new IllegalStateException(testName);
    }

    return inspection;
  }
}