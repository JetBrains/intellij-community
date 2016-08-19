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
package com.siyeh.ig.fixes.exceptions;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.exceptions.TryStatementWithMultipleResourcesInspection;

/**
 * @author Bas Leijdekkers
 */
public class TryStatementWithMultipleResourcesFixTest extends IGQuickFixesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new TryStatementWithMultipleResourcesInspection());
    myDefaultHint = InspectionGadgetsBundle.message("try.statement.with.multiple.resources.quickfix");
    myRelativePath = "exceptions/try_multiple_resource";
  }

  public void testSimple() {
    doTest();
  }

  public void testWithCatch() {
    doTest();
  }

  public void testWithFinally() {
    doTest();
  }

  public void testMixedResources() {
    doTest();
  }

  public void testAtFinalKeyword() {
    doTest();
  }

  public void testAtCodeBlock() {
    assertQuickfixNotAvailable();
  }
}
