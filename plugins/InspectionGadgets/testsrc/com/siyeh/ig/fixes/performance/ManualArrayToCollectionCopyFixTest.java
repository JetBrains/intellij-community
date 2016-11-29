/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.siyeh.ig.performance.ManualArrayToCollectionCopyInspection;

/**
 * @author Pavel.Dolgov
 */
public class ManualArrayToCollectionCopyFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ManualArrayToCollectionCopyInspection());
    myRelativePath = "performance/replace_with_add_all";
    myDefaultHint = InspectionGadgetsBundle.message("manual.array.to.collection.copy.replace.quickfix");
  }

  public void testSimpleFor() { doTest(); }
  public void testSimpleForeach() { doTest(); }
  public void testTempVarFor() { doTest(); }

  public void testPlusOne() { doTest(); }
  public void testPlusTwo() { doTest(); }

  public void testMinusOne() { doTest(); }
  public void testMinusTwo() { doTest(); }

  public void testMultiply() { doTest(); }
  public void testMultiplyLE() { doTest(); }

  public void testShift() { doTest(); }
  public void testShiftLE() { doTest(); }

  public void testUnary() { doTest(); }
  public void testUnary2() { doTest(); }
  public void testUnaryLE() { doTest(); }

  public void testForAtoB() { doTest(); }
  public void testPlusOneAtoB() { doTest(); }
  public void testPlusTwoAtoB() { doTest(); }
  public void testMinusOneAtoB() { doTest(); }
  public void testMinusTwoAtoB() { doTest(); }

  public void testPlusN() { doTest(); }
  public void testMinusN() { doTest(); }
}
