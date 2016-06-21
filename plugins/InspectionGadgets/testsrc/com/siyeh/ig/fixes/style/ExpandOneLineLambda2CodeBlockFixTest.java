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
package com.siyeh.ig.fixes.style;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.ExpandOneLineLambda2CodeBlockInspection;

public class ExpandOneLineLambda2CodeBlockFixTest extends IGQuickFixesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ExpandOneLineLambda2CodeBlockInspection());
    myDefaultHint = InspectionGadgetsBundle.message("expand.one.line.lambda2.code.block.quickfix");
    myRelativePath = "style/expr2block";
  }

  public void testSimple() {
    doTest();
  }

  public void testVoidCompatibleInExpr() {
    doTest();
  }
}
