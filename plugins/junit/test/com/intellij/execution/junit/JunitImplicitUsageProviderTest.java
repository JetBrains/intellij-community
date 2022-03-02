// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.execution.junit.codeInsight.JUnit5TestFrameworkSetupUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class JunitImplicitUsageProviderTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    JUnit5TestFrameworkSetupUtil.setupJUnit5Library(myFixture);
    myFixture.enableInspections(new UnusedDeclarationInspection(true));
  }

  public void testRecognizeTestMethodInParameterizedClass() {
    myFixture.configureByText("Test.java", "import org.junit.jupiter.params.provider.EnumSource;\n" +
                                           "\n" +
                                           "class Test {\n" +
                                           "  enum HostnameVerification {\n" +
                                           "    YES, NO;\n" +
                                           "  }\n" +
                                           "   @EnumSource(HostnameVerification.class)\n" +
                                           "   static void test(HostnameVerification hostnameVerification) {\n" +
                                           "        System.out.println(hostnameVerification);\n" +
                                           "    }\n" +
                                           "    public static void main(String[] args) {\n" +
                                           "        test(null);\n" +
                                           "    }\n" +
                                           "}");
    myFixture.testHighlighting(true, false, false);
  }

  public void testParameterizedDisplayNameByParameter() {
    myFixture.configureByText("MyTest.java", "import org.junit.jupiter.params.ParameterizedTest;" +
                                             "class MyTest {\n" +
                                             "@ParameterizedTest(name = \"{0}\")\n" +
                                             "void byName(String name) {}\n" +
                                             "}");
    myFixture.testHighlighting(true, false, false);
  }
}
