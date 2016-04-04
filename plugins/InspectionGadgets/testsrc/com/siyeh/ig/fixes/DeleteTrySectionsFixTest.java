/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.fixes;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.errorhandling.CaughtExceptionImmediatelyRethrownInspection;
import com.siyeh.ig.errorhandling.EmptyFinallyBlockInspection;

public class DeleteTrySectionsFixTest extends IGQuickFixesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new CaughtExceptionImmediatelyRethrownInspection(), new EmptyFinallyBlockInspection());
  }

  @Override
  protected String getRelativePath() {
    return "tryCatch";
  }

  public void testDeleteTryStatement() {
    doTest(InspectionGadgetsBundle.message("remove.try.catch.quickfix"));
  }

  public void testDeleteEmptyTryStatement() {
    doTest(InspectionGadgetsBundle.message("remove.try.catch.quickfix"));
  }

  public void testKeepComments() {
    doTest(InspectionGadgetsBundle.message("remove.try.catch.quickfix"));
  }

  public void testDeleteEmptyFinally() {
    doTest(InspectionGadgetsBundle.message("remove.finally.block.quickfix"));
  }

  public void testDeleteTryWithEmptyFinally() {
    doTest(InspectionGadgetsBundle.message("remove.finally.block.quickfix"));
  }

  public void testDeleteTryWithResources() {
    doTest(InspectionGadgetsBundle.message("delete.catch.section.quickfix"));
  }
}
