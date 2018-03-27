/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.fixes.performance;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.ToArrayCallWithZeroLengthArrayArgumentInspection;
import com.siyeh.ig.performance.ToArrayCallWithZeroLengthArrayArgumentInspection.PreferEmptyArray;

public class ToArrayCallWithZeroLengthArrayArgumentFixTest extends IGQuickFixesTestCase {
  private ToArrayCallWithZeroLengthArrayArgumentInspection myInspection = new ToArrayCallWithZeroLengthArrayArgumentInspection();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(myInspection);
  }

  public void testIntroduceVariable() {
    doFixTest(PreferEmptyArray.NEVER, InspectionGadgetsBundle.message("to.array.call.style.quickfix.make.presized"));
  }

  public void testPresizedToZero() {
    doFixTest(PreferEmptyArray.ALWAYS, InspectionGadgetsBundle.message("to.array.call.style.quickfix.make.zero"));
  }

  private void doFixTest(PreferEmptyArray mode, String message) {
    myInspection.myMode = mode;
    doTest(getTestName(false), message);
  }

  @Override
  protected String getRelativePath() {
    return "performance/to_array_call_with_zero_length_array_argument";
  }
}