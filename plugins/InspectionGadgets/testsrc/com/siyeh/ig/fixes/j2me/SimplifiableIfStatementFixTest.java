/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.fixes.j2me;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.j2me.SimplifiableIfStatementInspection;

/**
 * @author Bas Leijdekkers
 */
public class SimplifiableIfStatementFixTest extends IGQuickFixesTestCase {

  public void testComments() { doTest(); }
  public void testParentheses() { doTest(); }
  public void testMoreParentheses() { doTest(); }
  public void testPrecedence() { doTest(); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new SimplifiableIfStatementInspection());
    myRelativePath = "j2me/simplifiable_if_statement";
    myDefaultHint = InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix");
  }
}
