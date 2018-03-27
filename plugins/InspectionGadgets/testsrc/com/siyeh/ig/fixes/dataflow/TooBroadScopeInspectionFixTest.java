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

package com.siyeh.ig.fixes.dataflow;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.dataflow.TooBroadScopeInspection;

/**
 * @author Bas Leijdekkers
 */
public class TooBroadScopeInspectionFixTest extends IGQuickFixesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new TooBroadScopeInspection());
    myRelativePath = "dataflow/too_broad_scope";
  }

  public void testForStatement() { doTest(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "i")); }
  public void testForStatement2() { doTest(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "i")); }
  public void testForStatement3() { doTest(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "i")); }
  public void testTryResourceReference() { doTest(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "t")); }
  public void testComments() { doTest(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "s")); }
  public void testComments2() { doTest(InspectionGadgetsBundle.message("too.broad.scope.narrow.quickfix", "alpha")); }
}
