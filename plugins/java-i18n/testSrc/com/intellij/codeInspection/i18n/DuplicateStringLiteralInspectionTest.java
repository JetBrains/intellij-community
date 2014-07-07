/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInspection.i18n;

import com.intellij.codeInspection.duplicateStringLiteral.DuplicateStringLiteralInspection;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.InspectionTestCase;

public class DuplicateStringLiteralInspectionTest extends InspectionTestCase {
  private final DuplicateStringLiteralInspection myInspection = new DuplicateStringLiteralInspection();

  private void doTest() throws Exception {
    doTest("duplicateStringLiteral/"+getTestName(true), new LocalInspectionToolWrapper(myInspection), "java 1.5");
  }

  public void testPropertyKey() throws Exception{ myInspection.IGNORE_PROPERTY_KEYS = true; doTest(); }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("java-i18n") + "/testData/inspections";
  }
}