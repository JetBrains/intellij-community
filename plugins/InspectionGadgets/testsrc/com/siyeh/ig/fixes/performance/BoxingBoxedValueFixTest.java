// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.performance;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.BoxingBoxedValueInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class BoxingBoxedValueFixTest extends IGQuickFixesTestCase {

  @SuppressWarnings("BooleanConstructorCall")
  public void testConstructor() {
    doExpressionTest(InspectionGadgetsBundle.message("boxing.boxed.value.quickfix"),
                     "new Boolean/**/(Boolean.TRUE)", "Boolean.TRUE");
  }

  @SuppressWarnings("UnnecessaryBoxing")
  public void testMethod() {
    doExpressionTest(InspectionGadgetsBundle.message("boxing.boxed.value.quickfix"),
                     "Boolean.valueOf/**/(Boolean.TRUE)", "Boolean.TRUE");
  }

  @SuppressWarnings("UnnecessaryBoxing")
  public void testDoNotFixOnStringParameter() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("boxing.boxed.value.quickfix"),
                               "class X {\n" +
                               "  boolean field = Boolean/**/.valueOf(\"TRUE\");\n" +
                               "}\n");
  }

  @SuppressWarnings("BooleanConstructorCall")
  public void testDoNotFixOnWrongConstructor() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("boxing.boxed.value.quickfix"),
                               "class X {\n" +
                               "  boolean field = new /**/Boolean(\"TRUE\");\n" +
                               "}\n");
  }

  public void testDoNotFixOnWrongMethod() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("boxing.boxed.value.quickfix"),
                               "class X {\n" +
                               "  int field = Boolean/**/.compareTo(Boolean.TRUE);\n" +
                               "}\n");
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) throws Exception {
    builder.setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  @Override
  protected BaseInspection getInspection() {
    return new BoxingBoxedValueInspection();
  }
}
