// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;

public class ExtendsObjectFixTest extends IGQuickFixesTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ExtendsObjectInspection());
  }

  @SuppressWarnings("ClassExplicitlyExtendsObject")
  public void testClassExtendingObject() {
    doTest(InspectionGadgetsBundle.message("extends.object.remove.quickfix"),
           "public class MyClass extends /**/Object {}",
           "public class MyClass {}");
  }

  @SuppressWarnings("ClassExplicitlyExtendsObject")
  public void testClassExtendingJavaLangObject() {
    doTest(InspectionGadgetsBundle.message("extends.object.remove.quickfix"),
           "public class MyClass extends java.lang./**/Object {}",
           "public class MyClass {}");
  }

  public void testDoNotFixClassExtendingJavaUtilDate() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("extends.object.remove.quickfix"),
                               "public class MyClass extends java.util./**/Date {}\n");
  }

  @Override
  protected String getRelativePath() {
    return "style/extends_object";
  }
}