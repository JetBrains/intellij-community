/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
package com.siyeh.ig.fixes.errorhandling;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.errorhandling.CatchMayIgnoreExceptionInspection;

public class CatchMayIgnoreExceptionInspectionFixTest extends IGQuickFixesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CatchMayIgnoreExceptionInspection inspection = new CatchMayIgnoreExceptionInspection();
    inspection.m_ignoreCatchBlocksWithComments = false;
    myFixture.enableInspections(inspection);
  }

  @Override
  protected String getRelativePath() {
    return "errorhandling/ignore_exception";
  }

  public void testEmptyCatch() {
    doTest(InspectionGadgetsBundle.message("inspection.empty.catch.block.generate.body"));
  }

  public void testCatchWithComment() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("inspection.empty.catch.block.generate.body"));
  }

  public void testRenameToIgnored() {
    doTest("Rename 'ex' to 'ignored'");
  }

  public void testRenameToIgnoredJava21() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> doTest("Rename 'ex' to '_'"));
  }

  public void testRenameToIgnoredNameConflict() {
    doTest("Rename 'ex' to 'ignored1'");
  }
}
