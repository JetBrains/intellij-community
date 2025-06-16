// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Should be implemented by a test class together with the annotation {@code @RunWith(com.intellij.testFramework.Parameterized.class)}
 * in order to get test run on all test data files located in directory.
 * <p>
 * The desired directory can be configured by implementing
 * {@link FileBasedTestCaseHelperEx#getRelativeBasePath()}
 * or by annotating the test case class with {@link TestDataPath}.
 * Annotating with {@link TestDataPath} enables additional test assistance support, like:
 * <ul>
 *   <li>navigation from test data to the test class/method</li>
 *   <li>starting tests right from the test data files</li>
 * </ul>
 * N.B. {@code @RunWith} works also on abstract super classes.
 *
 * @see LightPlatformCodeInsightTestCase#params(Class)
 */
public interface FileBasedTestCaseHelper {
  /**
   * <h3>Example 1</h3>
   * Input: {@code afterMethodCanBeStatic.java}
   * <p>
   * Output: {@code null}
   * <h3>Example 2</h3>
   * Input: {@code beforeMethodCanBeStatic.java}
   * <p>
   * Output: {@code MethodCanBeStatic.java}
   *
   * @return the "core part" of the file name if the file is an "after" file, or null otherwise
   */
  @Nullable String getFileSuffix(@NotNull String fileName);

  /**
   * <h3>Example 1</h3>
   * Input: {@code afterMethodCanBeStatic.java}
   * <p>
   * Output: {@code MethodCanBeStatic.java}
   * <h3>Example 2</h3>
   * Input: {@code beforeMethodCanBeStatic.java}
   * <p>
   * Output: {@code null}
   *
   * @return the "core part" of the file name if the file is an "after" file, or null otherwise
   */
  default @Nullable String getBaseName(@NotNull String fileAfterSuffix) {
    return null;
  }
}
