// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
public class BoxingBoxedValueFixJava1d4Test extends IGQuickFixesTestCase {
  @SuppressWarnings("BoxingBoxedValue")
  public void testDoNotFixInJava1d4() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("boxing.boxed.value.quickfix"),
                               "class X {\n" +
                               "  boolean field = Boolean/**/.valueOf(Boolean.TRUE);\n" +
                               "}\n");
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) throws Exception {
    builder.setLanguageLevel(LanguageLevel.JDK_1_4);
  }

  @Override
  protected BaseInspection getInspection() {
    return new BoxingBoxedValueInspection();
  }
}
