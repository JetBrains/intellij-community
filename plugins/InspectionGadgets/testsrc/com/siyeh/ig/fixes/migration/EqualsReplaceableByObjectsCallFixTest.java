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
package com.siyeh.ig.fixes.migration;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.migration.EqualsReplaceableByObjectsCallInspection;

/**
 * @author Pavel.Dolgov
 */
public class EqualsReplaceableByObjectsCallFixTest extends IGQuickFixesTestCase {

  public void testSimpleEquals() { doTest(); }
  public void testSimpleNotEquals() { doTest(); }

  public void testQualifiedArgument() { doTest(); }
  public void testQualifiedReciever() { doTest(); }

  public void testExpressionReciever() { doTest(); }
  public void testExpressionArgument() { doTest(); }
  public void testExpressionArgument2() { doTest(); }

  public void testLongEquals() { doTest(); }
  public void testLongNotEquals() { doTest(); }
  public void testShortEquals() { doTest(); }
  public void testShortNotEquals() { doTest(); }

  public void testSuperEquals() { doTest(); }
  public void testThisEquals() { doTest(); }

  public void testQualifiedThisNotEqual() { doTest(); }
  public void testQualifiedSuperEqual() { doTest(); }

  public void testTernaryEquals() { doTest(); }
  public void testTernaryNotEquals() { doTest(); }

  public void testStaticField() { doTest(); }
  public void testStaticField2() { doTest(); }

  public void testArrayAccessEquals() { doTest(); }
  public void testArrayAccessTernaryEquals() { doTest(); }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) {
    builder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath())
      .setLanguageLevel(LanguageLevel.JDK_1_7);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new EqualsReplaceableByObjectsCallInspection());
    myRelativePath = "migration/equals_replaceable_by_objects_call";
    myDefaultHint = InspectionGadgetsBundle.message("equals.replaceable.by.objects.call.quickfix");
  }
}
