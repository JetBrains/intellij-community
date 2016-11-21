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
import com.siyeh.ig.performance.TailRecursionInspection;

/**
 * @author Bas Leijdekkers
 */
public class RemoveTailRecursionFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new TailRecursionInspection());
    myRelativePath = "performance/tail_recursion";
    myDefaultHint = InspectionGadgetsBundle.message("tail.recursion.replace.quickfix");
  }

  public void testCallOnOtherInstance1() { doTest(); }
  public void testCallOnOtherInstance2() { doTest(); }
  public void testDependency1() { doTest(); }
  public void testDependency2() { doTest(); }
  public void testDependency3() { doTest(); }
  public void testDependency4() { doTest(); }
  public void testThisVariable() { doTest(); }
  public void testUnmodifiedParameter() { doTest(); }
}
