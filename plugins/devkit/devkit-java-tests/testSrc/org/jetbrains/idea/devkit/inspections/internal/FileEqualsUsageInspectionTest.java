package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.PluginModuleTestCase;

public class FileEqualsUsageInspectionTest extends PluginModuleTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myFixture.enableInspections(new FileEqualsUsageInspection());
    myFixture.addClass("package com.intellij.openapi.util.io; public class FileUtil {}");
  }

  public void testEquals() {
    doTest("equals(null)", true);
  }

  public void testCompareTo() {
    doTest("compareTo(null)", true);
  }

  public void testHashCode() {
    doTest("hashCode()", true);
  }

  public void testGetName() {
    doTest("getName()", false);
  }

  private void doTest(String methodExpressionText, boolean highlightError) {
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

    myFixture.configureByText("Testing.java",
                              "public class Testing {" +
                              "  public void method() {" +
                              "     java.io.File file = null;" +
                              "     file." + expectedMethodExpression + ";" +
                              "  }" +
                              "}");
    myFixture.testHighlighting();
  }
}