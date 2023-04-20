// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.errorhandling;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.errorhandling.ThrowableSupplierOnlyThrowExceptionInspection;

public class ThrowableSupplierOnlyThrowExceptionInspectionFixTest extends IGQuickFixesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ThrowableSupplierOnlyThrowExceptionInspection());
  }

  @Override
  protected String getRelativePath() {
    return "errorhandling/throwable_supplier_only_throw_exception";
  }

  public void testSimple() {
    doTest(InspectionGadgetsBundle.message("throwable.supplier.only.throw.exception.quickfix"));
  }
  public void testSeveral() {
    doTest(InspectionGadgetsBundle.message("throwable.supplier.only.throw.exception.all.quickfix"));
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) {
    builder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath())
      .setLanguageLevel(LanguageLevel.JDK_1_8);
  }
}
