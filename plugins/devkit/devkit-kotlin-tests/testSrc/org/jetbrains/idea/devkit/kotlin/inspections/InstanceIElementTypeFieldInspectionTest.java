// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections;

public class InstanceIElementTypeFieldInspectionTest extends InstanceIElementTypeFieldInspectionTestBase {

  @Override
  protected String getFileExtension() {
    return "java";
  }

  public void testInstanceFieldWithIElementType() {
    doTest();
  }

  public void testStaticFieldNoWarning() {
    doTest();
  }

  public void testSubtypeDetection() {
    doTest();
  }

  public void testQuickFix() {
    doTest("Make field 'static final'");
  }

  public void testEnumFieldsNoWarning() {
    doTest();
  }
}
