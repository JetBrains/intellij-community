// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit.codeInsight;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class JUnit5MalformedParameterizedArgumentsTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return "/plugins/junit/testData/codeInsight/malformedParameterized/streamArgumentsMethodFix";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    JUnit5TestFrameworkSetupUtil.setupJUnit5Library(myFixture);
    myFixture.enableInspections(new JUnit5MalformedParameterizedInspection());
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  public void testStreamArgumentsMethod() { doTest(); }

  private void doTest() {
    final String name = getTestName(false);
    myFixture.configureByFile(name + ".java");
    myFixture.launchAction(myFixture.findSingleIntention("Create method 'Stream parmeters()'"));
    myFixture.checkResultByFile(name + ".after.java");
  }
}