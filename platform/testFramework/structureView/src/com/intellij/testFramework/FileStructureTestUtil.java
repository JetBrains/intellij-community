// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * File structure popup checks for a JUnit 5 test.
 * This is the replacement of {@link FileStructureTestBase}.
 * <p>
 * The test creates a {@link FileStructureTestFixture} for {@code fixture} before each test, and disposes it on the EDT after each test.
 * The test data files are {@code <name>.<extension>} and {@code <name>.tree} in the test data path of {@code fixture}.
 * The test method name gives {@code name}. For example, {@code testSimple} gives {@code Simple}.
 */
@TestOnly
public final class FileStructureTestUtil {
  private FileStructureTestUtil() {
  }

  /**
   * Configures the file {@code <name>.<fileExtension>} from the test data path of {@code fixture}.
   * Call it from any thread.
   */
  public static void configureDefault(@NotNull CodeInsightTestFixture fixture,
                                      @NotNull String testMethodName,
                                      @NotNull String fileExtension) {
    fixture.configureByFile(PathUtil.makeFileName(getTestName(testMethodName), fileExtension));
  }

  /**
   * Configures the default file with {@link #configureDefault}, then runs {@link #checkConfiguredTree} on the EDT.
   * Call it from any thread.
   */
  public static void checkTree(@NotNull CodeInsightTestFixture fixture,
                               @NotNull FileStructureTestFixture popupFixture,
                               @NotNull String testMethodName,
                               @NotNull String fileExtension,
                               @Nullable String filter,
                               boolean expandAll) {
    configureDefault(fixture, testMethodName, fileExtension);
    EdtTestUtil.runInEdtAndWait(() -> checkConfiguredTree(fixture, popupFixture, testMethodName, filter, expandAll));
  }

  /**
   * Updates the popup for the configured file and selects the element at the caret.
   * Then applies {@code filter} when it is not {@code null}, expands all nodes when {@code expandAll} is {@code true},
   * and checks the tree with {@link #checkResult}.
   * Call it on the EDT.
   */
  public static void checkConfiguredTree(@NotNull CodeInsightTestFixture fixture,
                                         @NotNull FileStructureTestFixture popupFixture,
                                         @NotNull String testMethodName,
                                         @Nullable String filter,
                                         boolean expandAll) {
    popupFixture.updateAndSelectCurrent();
    if (filter != null) {
      setSearchFilter(popupFixture, filter);
    }
    if (expandAll) {
      PlatformTestUtil.expandAll(popupFixture.getTree());
    }
    checkResult(fixture, popupFixture, testMethodName);
  }

  /**
   * Sets {@code filter} as the search filter of the popup, updates the popup, and selects the first element that matches.
   * Call it on the EDT.
   */
  public static void setSearchFilter(@NotNull FileStructureTestFixture popupFixture, @NotNull String filter) {
    popupFixture.getPopup().setSearchFilterForTests(filter);
    popupFixture.update();
    popupFixture.getSpeedSearch().findAndSelectElement(filter);
  }

  /**
   * Waits for the popup tree, and compares the printed tree with the file {@code <name>.tree} in the test data path of {@code fixture}.
   * Call it on the EDT.
   */
  public static void checkResult(@NotNull CodeInsightTestFixture fixture,
                                 @NotNull FileStructureTestFixture popupFixture,
                                 @NotNull String testMethodName) {
    String expectedFileName = fixture.getTestDataPath() + "/" + PathUtil.makeFileName(getTestName(testMethodName), "tree");
    PlatformTestUtil.waitWhileBusy(popupFixture.getTree());
    PlatformTestUtil.assertSameLinesWithFile(expectedFileName, PlatformTestUtil.print(popupFixture.getTree(), true).trim());
  }

  /**
   * Returns the test method name without the {@code test} prefix. The first letter keeps its case.
   */
  public static @NotNull String getTestName(@NotNull String testMethodName) {
    return PlatformTestUtil.getTestName(testMethodName, false);
  }
}
