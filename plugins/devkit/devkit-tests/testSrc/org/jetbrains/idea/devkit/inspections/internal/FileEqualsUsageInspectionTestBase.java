// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.PluginModuleTestCase;

public abstract class FileEqualsUsageInspectionTestBase extends PluginModuleTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new FileEqualsUsageInspection());
    myFixture.addClass("package com.intellij.openapi.util.io; public class FileUtil {}");
  }

  protected void doTest(String methodExpressionText, boolean highlightError) {
    String expectedMethodExpression = getMethodExpressionText(methodExpressionText, highlightError);
    doTest(expectedMethodExpression);
  }

  protected abstract void doTest(String expectedMethodExpression);

  protected static String getMethodExpressionText(String methodExpressionText, boolean highlightError) {
    String expectedMethodExpression;
    if (highlightError) {
      String methodName = StringUtil.substringBefore(methodExpressionText, "(");
      String methodParams = StringUtil.substringAfter(methodExpressionText, methodName);
      expectedMethodExpression = "<warning descr=\"" + DevKitBundle.message("inspections.file.equals.method") + "\">" +
                                 methodName +
                                 "</warning>" +
                                 methodParams;
    }
    else {
      expectedMethodExpression = methodExpressionText;
    }
    return expectedMethodExpression;
  }
}
