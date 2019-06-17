// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roland Illig
 */
public class NegatedConditionalInspectionTest extends LightJavaInspectionTestCase {

  private final NegatedConditionalInspection myInspection = new NegatedConditionalInspection();

  public void testNegatedConditionalNullAndZeroAllowed() {
    myInspection.m_ignoreNegatedNullComparison = true;
    myInspection.m_ignoreNegatedZeroComparison = true;

    doTest();
  }

  public void testNegatedConditionalNullAndZeroDisallowed() {
    myInspection.m_ignoreNegatedNullComparison = false;
    myInspection.m_ignoreNegatedZeroComparison = false;

    doTest();
  }

  public void testNegatedConditionalNullAllowed() {
    myInspection.m_ignoreNegatedNullComparison = true;
    myInspection.m_ignoreNegatedZeroComparison = false;

    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return myInspection;
  }
}