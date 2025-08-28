// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/useIntelliJVirtualThreads")
public class UseIntelliJVirtualThreadsInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/useIntelliJVirtualThreads";
  }
  
  @Override
  protected com.intellij.testFramework.LightProjectDescriptor getProjectDescriptor() {
    return new com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor(() ->
      com.intellij.testFramework.IdeaTestUtil.getMockJdk(com.intellij.util.lang.JavaVersion.compose(21))
    );
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UseIntelliJVirtualThreadsInspection());

    // Provide IntelliJVirtualThreads class used by the inspection
    myFixture.addClass(
      "package com.intellij.virtualThreads;" +
      "public final class IntelliJVirtualThreads {" +
      "  private IntelliJVirtualThreads() {}" +
      "  public static java.lang.Thread.Builder ofVirtual() { return null; }" +
      "}"
    );
  }

  public void testUseIntelliJVirtualThreads() {
    myFixture.testHighlighting(getTestName(false) + ".java");
  }
}
