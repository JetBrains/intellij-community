// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class JunitUnusedEnumSourceTest extends LightJavaCodeInsightFixtureTestCase {
@Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package org.junit.jupiter.params.provider; public @interface EnumSource {Class value();}");
    myFixture.enableInspections(new UnusedDeclarationInspection(true));
  }

  public void testRecognizeTestMethodInParameterizedClass() {
    myFixture.configureByText("Test.java", "import org.junit.jupiter.params.provider.EnumSource;\n" +
                                           "\n" +
                                           "class Test {\n" +
                                           "\n" +
                                           "  enum HostnameVerification {\n" +
                                           "    YES, NO;\n" +
                                           "  }\n" +
                                           "\n" +
                                           "   @EnumSource(HostnameVerification.class)\n" +
                                           "   static void test(HostnameVerification hostnameVerification) {\n" +
                                           "        System.out.println(hostnameVerification);\n" +
                                           "    }\n" +
                                           "\n" +
                                           "    public static void main(String[] args) {\n" +
                                           "        test(null);\n" +
                                           "    }\n" +
                                           "}");
    myFixture.testHighlighting(true, false, false);
  }
}
