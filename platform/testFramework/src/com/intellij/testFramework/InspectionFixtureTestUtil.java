// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * Inspection checks on a test data directory for a JUnit 5 test.
 * This is the JUnit 5 replacement of {@link InspectionFixtureTestCase}.
 */
@TestOnly
public final class InspectionFixtureTestUtil {
  private InspectionFixtureTestUtil() {
  }

  /**
   * Runs the local inspection {@code tool} on the test data directory {@code folderName} and checks the result.
   * See {@link #doTest(CodeInsightTestFixture, String, InspectionToolWrapper)}.
   * Call it from any thread.
   */
  public static void doTest(@NotNull CodeInsightTestFixture fixture, @NonNls @NotNull String folderName, @NotNull LocalInspectionTool tool) {
    doTest(fixture, folderName, new LocalInspectionToolWrapper(tool));
  }

  /**
   * Copies {@code <folderName>/src} from the test data path of {@code fixture} to the project.
   * Then runs the inspection of {@code toolWrapper} on the copied directory,
   * and compares the problems with {@code <folderName>/expected.xml}.
   * See {@link CodeInsightTestFixture#testInspection(String, InspectionToolWrapper)}.
   * Call it from any thread. The inspection runs on the EDT.
   */
  public static void doTest(@NotNull CodeInsightTestFixture fixture,
                            @NonNls @NotNull String folderName,
                            @NotNull InspectionToolWrapper<?, ?> toolWrapper) {
    EdtTestUtil.runInEdtAndWait(() -> fixture.testInspection(folderName, toolWrapper));
  }
}
