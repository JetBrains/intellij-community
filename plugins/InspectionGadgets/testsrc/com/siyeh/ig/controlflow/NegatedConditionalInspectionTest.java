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
    final NegatedConditionalInspection inspection = new NegatedConditionalInspection();
    inspection.m_ignoreNegatedNullComparison = true;
    inspection.m_ignoreNegatedZeroComparison = true;
    myFixture.enableInspections(inspection);

    doTest();
  }

  public void testNegatedConditionalNullAndZeroDisallowed() {
    final NegatedConditionalInspection inspection = new NegatedConditionalInspection();
    inspection.m_ignoreNegatedNullComparison = false;
    inspection.m_ignoreNegatedZeroComparison = false;
    myFixture.enableInspections(inspection);

    doTest();
  }

  public void testNegatedConditionalNullAllowed() {
    final NegatedConditionalInspection inspection = new NegatedConditionalInspection();
    inspection.m_ignoreNegatedNullComparison = true;
    inspection.m_ignoreNegatedZeroComparison = false;
    myFixture.enableInspections(inspection);

    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    // Only used to determine the path of the test Java files.
    return new NegatedConditionalInspection();
  }
}