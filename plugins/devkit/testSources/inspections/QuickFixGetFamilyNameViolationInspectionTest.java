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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

/**
 * @author Dmitry Batkovich
 */
@TestDataPath("$CONTENT_ROOT/testData/inspections/getFamilyNameViolation")
public class QuickFixGetFamilyNameViolationInspectionTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("devkit") + "/testData/inspections/getFamilyNameViolation";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(QuickFixGetFamilyNameViolationInspection.class);
    myFixture.addClass("package com.intellij.codeInspection;" +
                       "public interface QuickFix {" +
                       "  String getName();" +
                       "  String getFamilyName();" +
                       "}");
  }

  public void testViolationByField() {
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testViolationByGetName() {
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testViolationByExternalParameter() {
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testNotViolatedStaticField() {
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testNotViolatedStaticMethod() {
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testNotViolatedGetNameMethod() {
    myFixture.testHighlighting(getTestName(false) + ".java");
  }
}
