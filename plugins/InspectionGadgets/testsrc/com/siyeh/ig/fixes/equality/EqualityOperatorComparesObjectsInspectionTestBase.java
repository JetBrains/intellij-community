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
package com.siyeh.ig.fixes.equality;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.equality.EqualityOperatorComparesObjectsInspection;

/**
 * @see EqualityOperatorComparesObjectsInspection
 * @author Bas Leijdekkers
 */
public abstract class EqualityOperatorComparesObjectsInspectionTestBase extends IGQuickFixesTestCase {

  public void testEnumComparison() { assertQuickfixNotAvailable(); }
  public void testNullComparison() { assertQuickfixNotAvailable(); }
  public void testPrimitiveComparison() { assertQuickfixNotAvailable(); }
  public void testSimpleObjectComparison() { doTest(true, false); }
  public void testNegatedObjectComparison() { doTest(false, false); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new EqualityOperatorComparesObjectsInspection());
    myDefaultHint = "Replace";
    myRelativePath = "equality/replace_equality_with_equals";
  }

  protected void doTest(boolean isEqual, boolean isSafe) {
    doTest(InspectionGadgetsBundle.message(
      isSafe ? "equality.operator.compares.objects.safe.quickfix" : "equality.operator.compares.objects.quickfix",
      isEqual ? "==" : "!=",
      isEqual ? "" : "!"));
  }
}
