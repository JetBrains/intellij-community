// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.javadoc;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.javadoc.UnexpectedParamTagOrderInspection;

public class UnexpectedParamTagOrderInspectionFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnexpectedParamTagOrderInspection());
    myRelativePath = "javadoc/unexpected_param_tag_order";
    myDefaultHint = InspectionGadgetsBundle.message("inspection.unexpected.param.tag.order.quickfix");
  }

  public void testAdditionalMethodJavadocTags() { doTest(); }

  public void testAdditionalMethodParamTags() { doTest(); }

  public void testMissingMethodParamTags() { doTest(); }

  public void testDuplicateMethodParamTags() { doTest(); }

  public void testWrongOrderedMethodParamTags() { doTest(); }

  public void testAdditionalClassJavadocTags() { doTest(); }

  public void testAdditionalClassParamTags() { doTest(); }

  public void testMissingClassParamTags() { doTest(); }

  public void testDuplicateClassParamTags() { doTest(); }

  public void testWrongOrderedClassParamTags() { doTest(); }
}
