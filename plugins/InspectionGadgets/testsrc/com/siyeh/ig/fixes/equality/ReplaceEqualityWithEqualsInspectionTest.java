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

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.equality.ReplaceEqualityWithEqualsInspection;

/**
 * @see ReplaceEqualityWithEqualsInspection
 * @author Bas Leijdekkers
 */
public class ReplaceEqualityWithEqualsInspectionTest extends IGQuickFixesTestCase {

  public void testEnumComparison() { assertQuickfixNotAvailable(); }
  public void testNullComparison() { assertQuickfixNotAvailable(); }
  public void testPrimitiveComparison() { assertQuickfixNotAvailable(); }
  public void testSimpleObjectComparison() { doTest(InspectionGadgetsBundle.message("replace.equality.with.equals.descriptor", "==", "")); }
  public void testNegatedObjectComparison() { doTest(InspectionGadgetsBundle.message("replace.equality.with.equals.descriptor", "!=", "!")); }

  public void testSimpleObjectSafeComparison() { doTest(InspectionGadgetsBundle.message("replace.equality.with.safe.equals.descriptor", "==", "")); }
  public void testNegatedObjectSafeComparison() { doTest(InspectionGadgetsBundle.message("replace.equality.with.safe.equals.descriptor", "!=", "!")); }
  public void testSimpleObjectOldSafeComparison() { doTest(InspectionGadgetsBundle.message("replace.equality.with.safe.equals.descriptor", "==", "")); }
  public void testNegatedObjectOldSafeComparison() { doTest(InspectionGadgetsBundle.message("replace.equality.with.safe.equals.descriptor", "!=", "!")); }

    @Override
    protected void tuneFixture(JavaModuleFixtureBuilder builder) throws Exception {
      super.tuneFixture(builder);
      if (getTestName(false).contains("Old")) {
        builder.setLanguageLevel(LanguageLevel.JDK_1_6);
      }
      else if (getTestName(false).contains("Safe")) {
        builder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath()); // MockJdk17 would work if it contained java.util.Objects
        builder.setLanguageLevel(LanguageLevel.JDK_1_7);
      }
    }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ReplaceEqualityWithEqualsInspection());
    myDefaultHint = "Replace";
    myRelativePath = "equality/replace_equality_with_equals";
  }
}