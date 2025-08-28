// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/useIntelliJVirtualThreads")
public class UseIntelliJVirtualThreadsInspectionFixTest extends org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixTestBase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/useIntelliJVirtualThreads";
  }

  @Override
  protected @NotNull String getFileExtension() {
    return "java";
  }

  @Override
  protected com.intellij.testFramework.LightProjectDescriptor getProjectDescriptor() {
    return new DefaultLightProjectDescriptor(() -> IdeaTestUtil.getMockJdk(JavaVersion.compose(21)));
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
    doTest(org.jetbrains.idea.devkit.DevKitBundle.message("inspection.use.intellij.virtual.threads.fix.family.name"));
  }
}
